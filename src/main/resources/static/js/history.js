(() => {
  'use strict';
  const $ = (s) => document.querySelector(s);
  function h(tag, cls, text) { const n = document.createElement(tag); if (cls) n.className = cls; if (text != null) n.textContent = text; return n; }

  function toast(msg) { const t = $('#toast'); t.textContent = msg; t.hidden = false; setTimeout(() => { t.hidden = true; }, 6000); }

  async function load(q) {
    const list = $('#list');
    try {
      const res = await fetch('/api/history' + (q ? '?q=' + encodeURIComponent(q) : ''));
      if (!res.ok) throw new Error('Could not load history');
      const items = await res.json();
      list.replaceChildren();
      if (!items.length) { list.append(h('p', 'empty', q ? 'No reviews match your search.' : 'No reviews yet. Analyse some code and it will appear here.')); return; }
      items.forEach((r) => {
        const row = h('div', 'row');
        const top = h('div', 'top');
        top.append(h('span', 'title', r.title), h('span', 'badge', r.mode), h('span', 'badge ' + (r.severity === 'NONE' ? 'good' : 'warn'), r.severity || 'NONE'));
        if (r.status === 'FAILED') top.append(h('span', 'badge bad', 'AI failed'));
        row.append(top, h('div', 'muted small', new Date(r.createdAt).toLocaleString()), h('div', null, r.summary || ''));
        const btns = h('div', 'btns');
        const open = h('a', null, 'Open'); open.href = '/?review=' + r.id;
        const rep = h('a', null, 'Report (.md)'); rep.href = '/api/history/' + r.id + '/report';
        const del = h('button', null, 'Delete');
        del.addEventListener('click', async () => {
          if (!confirm('Delete this review and its chat?')) return;
          const d = await fetch('/api/history/' + r.id, { method: 'DELETE' });
          if (d.ok) load($('#search').value); else toast('Could not delete the review');
        });
        btns.append(open, rep, del); row.append(btns); list.append(row);
      });
    } catch (e) { toast(e.message); }
  }

  let timer;
  $('#search').addEventListener('input', (e) => { clearTimeout(timer); timer = setTimeout(() => load(e.target.value.trim()), 250); });
  load('');
})();
