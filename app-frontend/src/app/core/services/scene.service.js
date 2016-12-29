export default (app) => {
    class SceneService {
        constructor($resource) {
            'ngInject';

            this.Scene = $resource(
                '/api/scenes/:id/', {
                    id: '@properties.id'
                }, {
                    query: {
                        method: 'GET',
                        cache: false
                    },
                    get: {
                        method: 'GET',
                        cache: false
                    }
                }
            );
        }

        query(params = {}) {
            let validParams = Object.assign(
                params,
                {minCloudCover: params.minCloudCover ? params.minCloudCover : 0}
            );
            return this.Scene.query(validParams).$promise;
        }

        getSceneBounds(scene) {
            let boundsGeoJson = L.geoJSON();
            boundsGeoJson.addData(scene.dataFootprint);
            return boundsGeoJson.getBounds();
        }
    }

    app.service('sceneService', SceneService);
};
