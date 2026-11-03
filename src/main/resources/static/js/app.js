(() => {
  'use strict';

  const MONACO_PATH = 'https://cdn.jsdelivr.net/npm/monaco-editor@0.52.2/min/vs';
  const STARTER = [
    'public class Main {',
    '    public static void main(String[] args) {',
    '        int number = "Hello";',
    '        System.out.println(number);',
    '    }',
    '}',
    ''].join('\n');

  const $ = (s) => document.querySelector(s);
  const state = { files: [{ name: 'Main.java', content: STARTER }], active: 0, review: null, ed: null, diff: null, corrIndex: 0 };

  // ---------- small helpers (all output goes through textContent: AI text is never trusted as HTML) ----------
  function h(tag, cls, text) {
    const n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = text;
    return n;
  }
  const clear = (n) => { n.replaceChildren(); return n; };

  async function api(url, body, method) {
    const res = await fetch(url, {
      method: method || (body ? 'POST' : 'GET'),
      headers: body ? { 'Content-Type': 'application/json' } : {},
      body: body ? JSON.stringify(body) : undefined
    });
    if (!res.ok) {
      let msg = res.statusText;
      try { const p = await res.json(); msg = p.detail || p.title || msg; } catch (_) { /* not JSON */ }
      throw new Error(msg);
    }
    return (res.headers.get('content-type') || '').includes('json') ? res.json() : res.text();
  }

  let toastTimer;
  function toast(msg) {
    const t = $('#toast');
    t.textContent = msg; t.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { t.hidden = true; }, 7000);
  }

  function busy(on, text) {
    ['#analyse', '#compileOnly', '#genTests', '#chatSend'].forEach((s) => { $(s).disabled = on; });
    const b = $('#busy'); b.hidden = !on; b.textContent = text || '';
  }

  function download(name, text) {
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([text], { type: 'text/plain' }));
    a.download = name; a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
  }
  async function copy(text) {
    try { await navigator.clipboard.writeText(text); toast_ok('Copied'); } catch (_) { toast('Copy failed. Select the text and copy it manually.'); }
  }
  function toast_ok(msg) {
    const t = $('#toast'); t.style.background = 'var(--ok)'; t.textContent = msg; t.hidden = false;
    clearTimeout(toastTimer); toastTimer = setTimeout(() => { t.hidden = true; t.style.background = ''; }, 1500);
  }

  // ---------- editor (Monaco, with a textarea fallback) ----------
  function initEditor() {
    if (window.require && window.require.config) {
      require.config({ paths: { vs: MONACO_PATH } });
      require(['vs/editor/editor.main'], () => {
        const dark = window.matchMedia('(prefers-color-scheme: dark)').matches;
        const inst = monaco.editor.create($('#editor'), {
          value: state.files[0].content, language: 'java', theme: dark ? 'vs-dark' : 'vs',
          automaticLayout: true, minimap: { enabled: false }, fontSize: 14, scrollBeyondLastLine: false, glyphMargin: true
        });
        state.ed = { monaco: true, inst, get: () => inst.getValue(), set: (v) => inst.setValue(v) };
        boot();
      }, fallbackEditor);
    } else {
      fallbackEditor();
    }
  }
  function fallbackEditor() {
    if (state.ed) return;
    const ta = h('textarea', 'fallback'); ta.spellcheck = false; ta.value = state.files[0].content;
    clear($('#editor')).append(ta);
    state.ed = { monaco: false, get: () => ta.value, set: (v) => { ta.value = v; } };
    toast('Code editor could not be loaded (offline?). Using a plain text editor.');
    boot();
  }

  const cur = () => state.files[state.active];
  function saveCurrent() { cur().content = state.ed.get(); }

  function renderTabs() {
    const box = clear($('#tabs'));
    state.files.forEach((f, i) => {
      const t = h('div', 'tab' + (i === state.active ? ' on' : ''));
      const label = h('span', null, f.name);
      label.title = 'Double-click to rename';
      t.append(label);
      t.addEventListener('click', () => switchTo(i));
      label.addEventListener('dblclick', () => {
        const n = prompt('File name', f.name);
        if (n && /^[A-Za-z_$][A-Za-z0-9_$]*\.java$/.test(n.trim())) { f.name = n.trim(); renderTabs(); } else if (n) toast('Use a name like Main.java');
      });
      if (state.files.length > 1) {
        const x = h('button', 'x', '×'); x.title = 'Close file';
        x.addEventListener('click', (e) => { e.stopPropagation(); closeFile(i); });
        t.append(x);
      }
      box.append(t);
    });
  }
  function switchTo(i) {
    saveCurrent(); state.active = i; state.ed.set(cur().content); renderTabs(); applyMarkers();
  }
  function closeFile(i) {
    saveCurrent(); state.files.splice(i, 1);
    state.active = Math.min(state.active, state.files.length - 1);
    state.ed.set(cur().content); renderTabs(); applyMarkers();
  }
  function addFile(name, content) {
    saveCurrent(); state.files.push({ name, content }); state.active = state.files.length - 1;
    state.ed.set(content); renderTabs(); applyMarkers();
  }

  function applyMarkers() {
    if (!state.ed || !state.ed.monaco) return;
    const model = state.ed.inst.getModel();
    const r = state.review; const name = cur().name;
    const marks = []; const ai = [];
    if (r) {
      [r.originalCompilation].forEach((c) => (c.diagnostics || []).forEach((d) => {
        if (d.file === name && d.line > 0) {
          marks.push({ severity: d.kind === 'ERROR' ? monaco.MarkerSeverity.Error : monaco.MarkerSeverity.Warning,
            startLineNumber: d.line, startColumn: Math.max(d.column, 1), endLineNumber: d.line, endColumn: model.getLineMaxColumn(Math.min(d.line, model.getLineCount())), message: d.message });
        }
      }));
      if (r.originalFiles.length === 1 || r.originalFiles[state.active]?.name === name) {
        (r.ai.issues || []).forEach((i) => {
          if (i.line > 0 && i.line <= model.getLineCount()) {
            ai.push({ severity: monaco.MarkerSeverity.Info, startLineNumber: i.line, startColumn: 1, endLineNumber: i.line,
              endColumn: model.getLineMaxColumn(i.line), message: 'AI suggestion: ' + i.description });
          }
        });
      }
    }
    monaco.editor.setModelMarkers(model, 'javac', marks);
    monaco.editor.setModelMarkers(model, 'ai', ai);
  }
  function goTo(file, line) {
    const i = state.files.findIndex((f) => f.name === file);
    if (i >= 0 && i !== state.active) switchTo(i);
    if (state.ed.monaco && line > 0) { state.ed.inst.revealLineInCenter(line); state.ed.inst.setPosition({ lineNumber: line, column: 1 }); state.ed.inst.focus(); }
  }

  // ---------- results ----------
  function showTab(name) {
    document.querySelectorAll('.result-tabs button').forEach((b) => b.classList.toggle('on', b.dataset.tab === name));
    document.querySelectorAll('.panel').forEach((p) => p.classList.toggle('on', p.id === 'tab-' + name));
    if (name === 'corrected' && state.diff) state.diff.layout();
  }
  document.querySelectorAll('.result-tabs button').forEach((b) => b.addEventListener('click', () => showTab(b.dataset.tab)));

  function badge(text, kind) { return h('span', 'badge ' + (kind || ''), text); }

  function render(r) {
    state.review = r;
    renderOverview(r); renderCompiler(r.originalCompilation, $('#tab-compiler'), 'Compiler output for your code');
    renderFindings(r); renderCorrected(r); renderMore(r); applyMarkers(); loadChat(r.reviewId);
    showTab(r.originalCompilation.success && !(r.ai.issues || []).length ? 'overview' : (r.originalCompilation.success ? 'findings' : 'compiler'));
  }

  function renderOverview(r) {
    const p = clear($('#tab-overview'));
    const b = h('div', 'badges');
    const errs = r.originalCompilation.diagnostics.filter((d) => d.kind === 'ERROR').length;
    b.append(badge(r.originalCompilation.success ? 'Compiles' : errs + ' compile error' + (errs === 1 ? '' : 's'), r.originalCompilation.success ? 'good' : 'bad'));
    b.append(badge('Severity: ' + r.ai.severity, r.ai.severity === 'NONE' ? 'good' : (r.ai.severity === 'LOW' ? 'warn' : 'bad')));
    if (r.correctedFiles.length) b.append(badge(r.correctionVerified ? 'Correction compiles (' + r.fixAttempts + ' attempt' + (r.fixAttempts === 1 ? '' : 's') + ')' : 'Correction does not compile', r.correctionVerified ? 'good' : 'bad'));
    if (r.reviewId) b.append(badge('Saved #' + r.reviewId));
    p.append(b);
    if (r.aiError) p.append(h('div', 'notice', 'The AI step failed, but the compiler result is still valid. ' + r.aiError));
    p.append(h('p', null, r.ai.summary || 'No summary.'));
    if (r.ai.explanation) p.append(h('div', 'explain', r.ai.explanation));
    p.append(h('p', 'hint', r.disclaimer));
    if (r.reviewId) {
      const a = h('a', null, 'Download report (.md)'); a.href = '/api/history/' + r.reviewId + '/report'; p.append(a);
    }
  }

  function renderCompiler(c, panel, title) {
    clear(panel);
    const b = h('div', 'badges');
    b.append(badge(c.success ? 'Compilation successful' : 'Compilation failed', c.success ? 'good' : 'bad'), badge(c.durationMs + ' ms'));
    if (c.timedOut) b.append(badge('Timed out', 'bad'));
    panel.append(h('p', 'hint', title), b);
    if (!c.diagnostics.length) { panel.append(h('p', 'empty', c.success ? 'No errors or warnings.' : 'No diagnostics were reported.')); return; }
    c.diagnostics.forEach((d) => {
      const card = h('div', 'card ' + d.kind);
      const head = h('h4', null, d.kind.charAt(0) + d.kind.slice(1).toLowerCase() + ': ' + d.message.split('\n')[0]);
      card.append(head);
      if (d.line > 0) {
        const loc = h('button', 'loc', d.file + ':' + d.line + (d.column ? ':' + d.column : ''));
        loc.addEventListener('click', () => goTo(d.file, d.line)); card.append(loc);
      }
      if (d.sourceLine) card.append(h('pre', null, d.sourceLine));
      panel.append(card);
    });
  }

  function renderFindings(r) {
    const p = clear($('#tab-findings'));
    const issues = r.ai.issues || [];
    if (!issues.length) { p.append(h('p', 'empty', r.aiError ? 'AI findings are unavailable.' : 'The AI found no issues.')); return; }
    p.append(h('p', 'hint', 'AI suggestions. Compiler errors are verified; these are not, so check them before applying.'));
    issues.forEach((i) => {
      const card = h('div', 'card ' + i.severity);
      card.append(h('h4', null, i.severity + ' · ' + i.type));
      if (i.line > 0) { const l = h('button', 'loc', 'line ' + i.line); l.addEventListener('click', () => goTo(cur().name, i.line)); card.append(l); }
      card.append(h('p', null, i.description));
      if (i.suggestion) card.append(h('p', null, 'Fix: ' + i.suggestion));
      p.append(card);
    });
  }

  function renderMore(r) {
    const p = clear($('#tab-more'));
    const opt = r.ai.optimizations || [], tc = r.ai.testCases || [];
    if (!opt.length && !tc.length) { p.append(h('p', 'empty', 'No suggestions for this analysis.')); return; }
    if (opt.length) { p.append(h('h4', null, 'Optimisations and complexity')); const ul = h('ul'); opt.forEach((o) => ul.append(h('li', null, o))); p.append(ul); }
    if (tc.length) { p.append(h('h4', null, 'Suggested tests and exercises')); const ul = h('ul'); tc.forEach((o) => ul.append(h('li', null, o))); p.append(ul); }
  }

  function renderCorrected(r) {
    const has = r.correctedFiles.length > 0;
    $('#correctedEmpty').hidden = has; $('#correctedBox').hidden = !has;
    $('#correctedEmpty').textContent = r.aiError ? 'AI is unavailable, so no correction was generated.' : 'The AI proposed no code changes.';
    if (!has) return;
    const sel = clear($('#corrFile'));
    r.correctedFiles.forEach((f, i) => { const o = h('option', null, f.name); o.value = i; sel.append(o); });
    state.corrIndex = 0;
    const badgeBox = clear($('#corrBadge'));
    badgeBox.append(badge(r.correctionVerified ? 'Compiles' : 'Does not compile yet', r.correctionVerified ? 'good' : 'bad'));
    showDiff();
  }
  function showDiff() {
    const r = state.review; const f = r.correctedFiles[state.corrIndex];
    const orig = (r.originalFiles.find((o) => o.name === f.name) || { content: '' }).content;
    const box = $('#diff');
    if (state.ed.monaco) {
      if (state.diff) { const m = state.diff.getModel(); state.diff.dispose(); m.original.dispose(); m.modified.dispose(); }
      clear(box);
      const dark = window.matchMedia('(prefers-color-scheme: dark)').matches;
      state.diff = monaco.editor.createDiffEditor(box, { readOnly: true, automaticLayout: true, renderSideBySide: true, minimap: { enabled: false }, theme: dark ? 'vs-dark' : 'vs' });
      state.diff.setModel({ original: monaco.editor.createModel(orig, 'java'), modified: monaco.editor.createModel(f.content, 'java') });
    } else {
      clear(box).append(h('pre', null, f.content));
    }
  }
  $('#corrFile').addEventListener('change', (e) => { state.corrIndex = +e.target.value; showDiff(); });
  $('#copyCorr').addEventListener('click', () => copy(state.review.correctedFiles[state.corrIndex].content));
  $('#dlCorr').addEventListener('click', () => { const f = state.review.correctedFiles[state.corrIndex]; download(f.name, f.content); });
  $('#applyCorr').addEventListener('click', () => {
    const r = state.review; saveCurrent();
    r.correctedFiles.forEach((cf) => { const ex = state.files.find((f) => f.name === cf.name); if (ex) ex.content = cf.content; else state.files.push({ name: cf.name, content: cf.content }); });
    state.ed.set(cur().content); renderTabs(); toast_ok('Corrected code is now in the editor');
  });

  function renderTests(t) {
    const p = clear($('#tab-tests'));
    p.append(h('div', 'notice', t.note));
    if (t.summary) p.append(h('p', null, t.summary));
    if (t.scenarios.length) { p.append(h('h4', null, 'Scenarios')); const ul = h('ul'); t.scenarios.forEach((s) => ul.append(h('li', null, s))); p.append(ul); }
    t.testFiles.forEach((f) => {
      const head = h('div', 'file-head'); head.append(h('span', null, f.name));
      const bar = h('span'); const c = h('button', null, 'Copy'); const d = h('button', null, 'Download');
      c.addEventListener('click', () => copy(f.content)); d.addEventListener('click', () => download(f.name, f.content));
      bar.append(c, ' ', d); head.append(bar);
      p.append(head, h('pre', 'code', f.content));
    });
    if (!t.testFiles.length) p.append(h('p', 'empty', 'The model returned no test files. Try again.'));
  }

  // ---------- chat ----------
  function addMsg(role, text) { const m = h('div', 'msg ' + role, text); $('#chatLog').append(m); m.scrollIntoView({ block: 'end' }); return m; }
  async function loadChat(id) {
    const log = clear($('#chatLog'));
    if (!id) { log.append(h('p', 'empty', 'This review was not saved, so follow-up chat has no context. You can still ask general questions.')); return; }
    try { const msgs = await api('/api/chat/' + id); msgs.forEach((m) => addMsg(m.role, m.message)); } catch (_) { /* no history */ }
    if (!log.children.length) log.append(h('p', 'empty', 'Ask anything about this review, e.g. "Why does line 3 fail?"'));
  }
  async function sendChat() {
    const input = $('#chatText'); const text = input.value.trim(); if (!text) return;
    input.value = ''; const log = $('#chatLog'); log.querySelectorAll('.empty').forEach((e) => e.remove());
    addMsg('user', text); busy(true, 'Thinking…');
    try { const r = await api('/api/chat', { reviewId: state.review ? state.review.reviewId : null, message: text }); addMsg('assistant', r.reply); }
    catch (e) { toast(e.message); } finally { busy(false); }
  }
  $('#chatSend').addEventListener('click', sendChat);
  $('#chatText').addEventListener('keydown', (e) => { if (e.key === 'Enter') sendChat(); });

  // ---------- actions ----------
  const payload = () => { saveCurrent(); return { language: 'java', files: state.files.map((f) => ({ name: f.name, content: f.content })) }; };

  $('#analyse').addEventListener('click', async () => {
    busy(true, 'Compiling, then asking the AI… this can take a minute on a laptop.');
    try {
      const r = await api('/api/review', Object.assign(payload(), {
        mode: $('#mode').value, errorText: $('#errorText').value || null, question: $('#question').value || null, save: true }));
      render(r);
    } catch (e) { toast(e.message); } finally { busy(false); }
  });

  $('#compileOnly').addEventListener('click', async () => {
    busy(true, 'Compiling…');
    try {
      const c = await api('/api/compile', payload());
      state.review = state.review ? Object.assign({}, state.review, { originalCompilation: c }) : { originalCompilation: c, originalFiles: state.files, ai: { issues: [] }, correctedFiles: [] };
      renderCompiler(c, $('#tab-compiler'), 'Compiler output for your code (no AI)'); applyMarkers(); showTab('compiler');
    } catch (e) { toast(e.message); } finally { busy(false); }
  });

  $('#genTests').addEventListener('click', async () => {
    busy(true, 'Generating tests…');
    try { renderTests(await api('/api/tests', payload())); showTab('tests'); }
    catch (e) { toast(e.message); } finally { busy(false); }
  });

  $('#addFile').addEventListener('click', () => {
    const n = prompt('New file name', 'Helper.java');
    if (!n) return;
    if (!/^[A-Za-z_$][A-Za-z0-9_$]*\.java$/.test(n.trim())) return toast('Use a name like Helper.java');
    if (state.files.some((f) => f.name === n.trim())) return toast('A file with that name already exists');
    addFile(n.trim(), 'public class ' + n.trim().replace('.java', '') + " {\n}\n");
  });

  $('#upload').addEventListener('change', async (e) => {
    const files = [...e.target.files];
    for (const f of files) {
      if (!f.name.endsWith('.java')) { toast(f.name + ' is not a .java file'); continue; }
      if (f.size > 200 * 1024) { toast(f.name + ' is larger than 200 KB'); continue; }
      const text = await f.text();
      if (state.files.length === 1 && state.files[0].content === STARTER) { state.files = []; state.active = -1; }
      state.files.push({ name: f.name, content: text }); state.active = state.files.length - 1;
    }
    if (state.active >= 0) { state.ed.set(cur().content); renderTabs(); }
    e.target.value = '';
  });

  // ---------- startup ----------
  async function loadSaved(id) {
    try {
      const r = await api('/api/history/' + id);
      state.files = r.originalFiles.map((f) => ({ name: f.name, content: f.content })); state.active = 0;
      state.ed.set(cur().content); renderTabs(); $('#mode').value = r.mode; render(r);
    } catch (e) { toast(e.message); }
  }
  async function loadStatus() {
    try {
      const s = await api('/api/status');
      $('#dot-ollama').classList.add(s.ollama ? 'up' : 'down');
      $('#txt-ollama').textContent = s.ollama ? 'AI ready' : 'AI offline (compiler still works)';
      $('#txt-model').textContent = s.model;
    } catch (_) { $('#txt-ollama').textContent = 'Status unavailable'; }
  }
  let booted = false;
  function boot() {
    if (booted) return; booted = true;
    renderTabs(); loadStatus();
    const id = new URLSearchParams(location.search).get('review');
    if (id) loadSaved(id);
  }
  initEditor();
})();
