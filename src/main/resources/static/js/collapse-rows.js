/*
 * Thu gọn bảng dài (#362, #363).
 *
 * <table data-collapse-rows="5">     : chỉ hiện 5 dòng đầu.
 * <table data-collapse-window="1">   : chỉ hiện dòng có class "collapse-focus" (không có thì "is-current")
 *                                      và 1 dòng trước, 1 dòng sau.
 *
 * Nút "Xem thêm"/"Thu gọn" được chèn ngay sau bảng; bảng không đủ dài thì không có nút.
 */
(function () {
    function visibleIndexes(table, rows) {
        var windowSize = table.getAttribute('data-collapse-window');
        if (windowSize !== null) {
            var size = parseInt(windowSize, 10) || 0;
            var current = rows.findIndex(function (row) { return row.classList.contains('collapse-focus'); });
            if (current < 0) {
                current = rows.findIndex(function (row) { return row.classList.contains('is-current'); });
            }
            if (current < 0) {
                current = 0;
            }
            var from = Math.max(0, current - size);
            var to = Math.min(rows.length - 1, current + size);
            return function (index) { return index >= from && index <= to; };
        }
        var limit = parseInt(table.getAttribute('data-collapse-rows'), 10) || 5;
        return function (index) { return index < limit; };
    }

    function setup(table) {
        if (table.dataset.collapseReady) {
            return;
        }
        table.dataset.collapseReady = '1';
        var body = table.tBodies[0];
        if (!body) {
            return;
        }
        var rows = Array.prototype.slice.call(body.rows);
        var isVisible = visibleIndexes(table, rows);
        var hidden = rows.filter(function (row, index) { return !isVisible(index); });
        if (hidden.length === 0) {
            return;
        }
        var button = document.createElement('button');
        button.type = 'button';
        button.className = 'btn btn-sm btn-secondary collapse-toggle';
        var moreLabel = table.getAttribute('data-collapse-more') || ('Xem thêm (' + hidden.length + ')');
        var lessLabel = table.getAttribute('data-collapse-less') || 'Thu gọn';
        var expanded = false;
        function render() {
            hidden.forEach(function (row) { row.hidden = !expanded; });
            button.textContent = expanded ? lessLabel : moreLabel;
            button.setAttribute('aria-expanded', expanded ? 'true' : 'false');
        }
        button.addEventListener('click', function () {
            expanded = !expanded;
            render();
        });
        render();
        var holder = document.createElement('div');
        holder.className = 'collapse-toggle-row';
        holder.appendChild(button);
        table.insertAdjacentElement('afterend', holder);
    }

    function init() {
        document.querySelectorAll('table[data-collapse-rows], table[data-collapse-window]').forEach(setup);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
