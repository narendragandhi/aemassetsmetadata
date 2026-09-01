(function (document) {
    'use strict';
    function escapeHtml(value) {
        return String(value == null ? '' : value).replace(/[&<>\"']/g, function (c) {
            return ({'&':'&amp;', '<':'&lt;', '>':'&gt;', '\"':'&quot;', "'":'&#39;'})[c];
        });
    }
    function init(root) {
        var path = root.getAttribute('data-asset-path');
        var status = root.querySelector('.semanticdam-ai-review__status');
        var tbody = root.querySelector('tbody');
        function request(url, options) {
            return fetch(url, options).then(function (response) {
                if (!response.ok) { return response.text().then(function (body) { throw new Error(body || response.statusText); }); }
                return response.json();
            });
        }
        function load() {
            return request('/bin/aemassets/ai/proposals.json?path=' + encodeURIComponent(path)).then(render);
        }
        function render(data) {
            tbody.innerHTML = data.proposals.length ? data.proposals.map(function (p) {
                var decision = p.status === 'PROPOSED' ? '<textarea required rows="2" placeholder="Reason for decision" data-reason></textarea>'
                    + '<button type="button" class="coral3-Button coral3-Button--quiet" data-decision="true">Approve</button>'
                    + '<button type="button" class="coral3-Button coral3-Button--secondary" data-decision="false">Reject</button>' : '—';
                return '<tr data-predicate="' + escapeHtml(p.predicate) + '" data-object="' + escapeHtml(p.object) + '"><td><code>' + escapeHtml(p.predicate) + '</code><br>' + escapeHtml(p.object) + '</td>'
                    + '<td>' + escapeHtml(Math.round(p.confidence * 100)) + '%</td><td>' + escapeHtml(p.rationale) + '</td>'
                    + '<td><strong>' + escapeHtml(p.status) + '</strong><br><small>' + escapeHtml(p.modelId) + '</small></td><td>' + decision + '</td></tr>';
            }).join('') : '<tr><td colspan="5">No proposals returned. Check provider configuration and asset path.</td></tr>';
            status.textContent = data.proposals.length + ' proposal(s) loaded.';
        }
        root.querySelector('[data-ai-action="propose"]').addEventListener('click', function () {
            status.textContent = 'Generating proposals…';
            request('/bin/aemassets/ai/propose', {method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body: 'path=' + encodeURIComponent(path)})
                .then(render).catch(function (error) { status.textContent = 'Could not generate proposals: ' + error.message; });
        });
        root.addEventListener('click', function (event) {
            var button = event.target.closest('[data-decision]');
            if (!button) return;
            var row = button.closest('tr'), cells = row.querySelectorAll('td'), reason = row.querySelector('[data-reason]').value.trim();
            if (!reason) { status.textContent = 'A reason is required for every decision.'; return; }
            var body = 'path=' + encodeURIComponent(path) + '&predicate=' + encodeURIComponent(row.getAttribute('data-predicate'))
                + '&object=' + encodeURIComponent(row.getAttribute('data-object')) + '&approve=' + button.getAttribute('data-decision')
                + '&reason=' + encodeURIComponent(reason);
            request('/bin/aemassets/ai/review', {method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body: body})
                .then(function () { status.textContent = 'Decision saved.'; return load(); })
                .catch(function (error) { status.textContent = 'Could not save decision: ' + error.message; });
        });
        load().catch(function () { status.textContent = 'No proposal state is available yet.'; });
    }
    Array.prototype.forEach.call(document.querySelectorAll('.semanticdam-ai-review'), init);
}(document));
