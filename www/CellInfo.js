module.exports = (function() {
    var map = {};
    var def = function(method) {
        map[method] = function(success, failure) {
            cordova.exec(success, failure, 'CellInfo', method, Array.prototype.slice.call(arguments, 2));
        };
    };
    def('gsmCells');
    return map;
})();
