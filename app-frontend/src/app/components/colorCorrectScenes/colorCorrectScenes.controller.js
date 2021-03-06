/**
* From a lng coordinate and a zoom level, determine the x-coordinate of
* the tile in falls within
* @param {numeric} lng lng coordinate
* @param {numeric} zoom integer zoom level
*
* @returns {numeric} x-coordinate of tile
*/
let lng2Tile = function (lng, zoom) {
    return Math.floor((lng + 180) / 360 * Math.pow(2, zoom + 2));
};

/**
* From a lat coordinate and a zoom level, determine the y-coordinate of
* the tile in falls within
* @param {numeric} lat lat coordinate
* @param {numeric} zoom integer zoom level
*
* @returns {numeric} y-coordinate of tile
*/
let lat2Tile = function (lat, zoom) {
    return Math.floor((1 - Math.log(Math.tan(lat * Math.PI / 180) +
                                    1 / Math.cos(lat * Math.PI / 180))
                            / Math.PI) / 2 * Math.pow(2, zoom + 2));
};

/**
* From a tile coordinate and zoom level, determine the lng coordinate of
* the corresponding tile's NE corner
* @param {numeric} x x-coordinate of tile
* @param {numeric} zoom integer zoom level
*
* @returns {numeric} lng point of tile NE corner
*/
let tile2Lng = function (x, zoom) {
    return x / Math.pow(2, zoom + 2) * 360 - 180;
};

/**
* From a tile coordinate and zoom level, determine the lat coordinate of
* the corresponding tile's NE corner
* @param {numeric} y y-coordinate of tile
* @param {numeric} zoom integer zoom level
*
* @returns {numeric} lat point of tile NE corner
*/
let tile2Lat = function (y, zoom) {
    let n = Math.PI - 2 * Math.PI * y / Math.pow(2, zoom + 2);
    return 180 / Math.PI * Math.atan(0.5 * (Math.exp(n) - Math.exp(-n)));
};

export default class ColorCorrectScenesController {
    constructor( // eslint-disable-line max-params
        $log, $scope, $q, projectService, layerService, sceneService, $state, mapService
    ) {
        'ngInject';
        this.projectService = projectService;
        this.layerService = layerService;
        this.sceneService = sceneService;
        this.$state = $state;
        this.$scope = $scope;
        this.$parent = $scope.$parent.$ctrl;
        this.$q = $q;
        this.$log = $log;
        this.getMap = () => mapService.getMap('project');
    }

    $onInit() {
        // Internal bookkeeping to handle grid selection functionality.
        this.selectedTileX = null;
        this.selectedTileY = null;
        this.projectid = this.$state.params.projectid;
        this.gridLayer = L.gridLayer({
            tileSize: 64,
            className: 'grid-select'
        });
        this.gridLayer.setZIndex(100);

        this.getMap().then((map) => {
            this.listeners = [
                map.on('click', this.selectGridCellScenes.bind(this)),
                map.on('contextmenu', (evt, src) => {
                    evt.originalEvent.preventDefault();
                    if (evt.originalEvent.ctrlKey) {
                        this.selectGridCellScenes(evt, src);
                    }
                    return false;
                })
            ];
            map.addLayer('grid-selection-layer', this.gridLayer);
            this.selectedScenes.forEach((scene) => {
                map.setGeojson(scene.id, this.sceneService.getStyledFootprint(scene));
            });
        });

        this.bands = {
            natural: {
                label: 'Natural Color',
                value: {redBand: 3, greenBand: 2, blueBand: 1}
            },
            cir: {
                label: 'Color infrared',
                value: {redBand: 4, greenBand: 3, blueBand: 2}
            },
            urban: {
                label: 'Urban',
                value: {redBand: 6, greenBand: 5, blueBand: 4}
            },
            water: {
                label: 'Water',
                value: {redBand: 4, greenBand: 5, blueBand: 3}
            },
            atmosphere: {
                label: 'Atmosphere penetration',
                value: {redBand: 6, greenBand: 4, blueBand: 2}
            },
            agriculture: {
                label: 'Agriculture',
                value: {redBand: 5, greenBand: 4, blueBand: 1}
            },
            forestfire: {
                label: 'Forest Fire',
                value: {redBand: 6, greenBand: 4, blueBand: 1}
            },
            bareearth: {
                label: 'Bare Earth change detection',
                value: {redBand: 5, greenBand: 2, blueBand: 1}
            },
            vegwater: {
                label: 'Vegetation & water',
                value: {redBand: 4, greenBand: 6, blueBand: 0}
            }
        };
    }

    $onDestroy() {
        this.getMap().then((map) => {
            this.listeners.forEach((listener) => {
                map.off(listener);
            });
            map.deleteLayers('grid-select');
            map.deleteLayers('grid-selection-layer');
            this.selectedScenes.forEach((scene) => map.deleteGeojson(scene.id));
        });
    }

    /**
     * Select the scenes that fall within the clicked grid cell
     *
     * @param {object} $event from the map
     * @param {MapWrapper} source event source
     * @returns {undefined}
     */
    selectGridCellScenes($event, source) {
        let multi = $event.originalEvent.ctrlKey;
        // Cache current request kickoff time for identifying request
        let requestTime = new Date();

        // this.lastRequest will always hold the latest kickoff time
        this.lastRequest = requestTime;

        if (!this.filterBboxList || !this.filterBboxList.length) {
            this.filterBboxList = [];
        }


        let z = source.map.getZoom();

        // The conversion functions above return the upper-left (northwest) corner of the tile,
        // so to get the lower left and upper right corners to make a bounding box, we need to
        // get the upper-left corners of the tiles directly below and to the right of this one.
        let tx = lng2Tile($event.latlng.lng, z);
        let ty = lat2Tile($event.latlng.lat, z);

        let bounds = L.latLngBounds([
            [tile2Lat(ty, z), tile2Lng(tx + 1, z)],
            [tile2Lat(ty + 1, z), tile2Lng(tx, z)]
        ]);

        let filteredList = this.filterBboxList.filter(b => {
            return !b.equals(bounds) && !b.contains(bounds) && !bounds.contains(b);
        });

        if (filteredList.length === this.filterBboxList.length) {
            // If the clicked bounding box has not been selected
            if (!multi) {
                this.filterBboxList = [];
            }
            this.filterBboxList.push(bounds);
        } else if (!multi) {
            // If the clicked bounding box is already selected
            this.filterBboxList = [];
        } else {
            this.filterBboxList = filteredList;
        }

        this.updateGridSelection();

        if (this.filterBboxList.length) {
            this.projectService.getAllProjectScenes({
                projectId: this.projectid,
                bbox: this.filterBboxList.map(r => r.toBBoxString()).join(';')
            }).then((selectedScenes) => {
                if (this.lastRequest === requestTime) {
                    this.selectNoScenes();
                    selectedScenes.map((scene) => this.setSelected(scene, true));
                }
            });
        } else {
            this.selectNoScenes();
        }
    }

    /**
     * Set RGB bands for layers
     *
     * TODO: Only works for Landsat8 -- needs to be adjusted based on datasource
     *
     * @param {string} bandName name of band selected
     *
     * @returns {null} null
     */
    setBands(bandName) {
        this.mosaic = this.$parent.mosaicLayer.values().next().value;
        let promises = [];
        this.sceneLayers.forEach((layer) => {
            promises.push(layer.updateBands(this.bands[bandName].value));
        });
        this.redrawMosaic(promises, this.bands[bandName].value);
    }

    /**
     * Trigger the redraw of the mosaic layer with new bands
     *
     * @param {promise[]} promises array of scene color correction promises
     * @param {object} newBands new mapping of band numbers
     * @returns {null} null
     */
    redrawMosaic(promises, newBands) {
        if (!promises.length) {
            return;
        }
        this.mosaic.getColorCorrection().then((lastCorrection) => {
            let ccParams = this.mosaic.paramsFromObject(lastCorrection);
            return Object.assign(ccParams, newBands);
        }).then((newCorrection) => {
            this.$q.all(promises).then(() => {
                this.mosaic.getMosaicTileLayer().then((tiles) => {
                // eslint-disable-next-line max-nested-callbacks
                    this.mosaic.getMosaicLayerURL(newCorrection).then((url) => {
                        tiles.setUrl(url);
                    });
                });
            });
        });
    }

    onToggleSelection() {
        // This fires pre-change, so if the box is checked then we need to deselect
        if (this.shouldSelectAll()) {
            this.selectAllScenes();
        } else {
            this.selectNoScenes();
        }
    }

    shouldSelectAll() {
        return this.selectedScenes.size === 0 || this.selectedScenes.size < this.sceneList.size;
    }

    selectAllScenes() {
        this.sceneList.map((scene) => this.setSelected(scene, true));
    }

    selectNoScenes() {
        this.selectedScenes.forEach((scene) => this.setSelected(scene, false));
        this.$scope.$evalAsync();
    }

    isSelected(scene) {
        return this.selectedScenes.has(scene.id);
    }

    setSelected(scene, selected) {
        if (selected) {
            this.selectedScenes.set(scene.id, scene);
            this.selectedLayers.set(scene.id, this.sceneLayers.get(scene.id));
            this.getMap().then((map) => {
                map.setGeojson(scene.id, this.sceneService.getStyledFootprint(scene));
            });
        } else {
            this.selectedScenes.delete(scene.id);
            this.selectedLayers.delete(scene.id);
            this.getMap().then((map) => {
                map.deleteGeojson(scene.id);
            });
        }
    }

    setHoveredScene(scene) {
        this.onSceneMouseover({scene: scene});
    }

    removeHoveredScene() {
        this.onSceneMouseleave();
    }

    updateGridSelection() {
        if (!this.gridSelectionLayer) {
            this.gridSelectionLayer = L.featureGroup();
            this.getMap().then((map) => {
                map.addLayer('grid-selection-layer', this.gridSelectionLayer);
            });
        } else {
            this.gridSelectionLayer.clearLayers();
        }
        this.filterBboxList.forEach(b => L.rectangle(b).addTo(this.gridSelectionLayer));
    }

    goColorCorrect() {
        this.getMap().then((map) => {
            map.deleteLayers('grid-selection-layer');
        });
        this.gridSelectionLayer = null;
        this.$state.go('editor.project.color.adjust');
    }

    isActiveColorMode(key) {
        let layer = this.sceneLayers.values().next();
        let currentBands = {};
        if (layer && layer.value) {
            currentBands = layer.value.getCachedColorCorrection();
        }
        let keyBands = this.bands[key].value;

        let isActive = currentBands &&
            keyBands.redBand === currentBands.redBand &&
            keyBands.greenBand === currentBands.greenBand &&
            keyBands.blueBand === currentBands.blueBand;
        return isActive;
    }
}
