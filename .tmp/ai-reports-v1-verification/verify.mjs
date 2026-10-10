import { createRequire } from 'node:module';
import { spawn } from 'node:child_process';
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import { pathToFileURL } from 'node:url';

const repo = 'E:/Huong-Van-Tra-';
const dir = path.join(repo, '.tmp/ai-reports-v1-verification');
const app = path.join(repo, 'web-app/apps/pos-web');
const require = createRequire(path.join(app, 'package.json'));
const { createServer } = await import(pathToFileURL(require.resolve('vite')).href);
const html = '<!DOCTYPE html><html lang="vi"><head><meta charset="UTF-8"><title>AI Reports isolated UI verification</title></head><body><div id="root"></div><script type="module" src="/@fs/E:/Huong-Van-Tra-/.tmp/ai-reports-v1-verification/harness.tsx"></script></body></html>';
const server = await createServer({ root: app, configFile: path.join(app, 'vite.config.ts'), resolve: { alias: { react: path.join(app, 'node_modules/react'), 'react-dom': path.join(app, 'node_modules/react-dom'), 'react-router-dom': path.join(app, 'node_modules/react-router-dom') } }, optimizeDeps: { include: ['react', 'react/jsx-dev-runtime', 'react-dom/client', 'react-router-dom'] }, plugins: [{ name: 'isolated-ai-verification', configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
  if (!req.url.startsWith('/system') && !req.url.startsWith('/management/')) return next();
  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.end(await vite.transformIndexHtml(req.url, html));
}); } }], server: { host: '127.0.0.1', port: 0, fs: { allow: [repo] } } });
await server.listen();
const baseUrl = `http://127.0.0.1:${server.httpServer.address().port}`;
const profile = path.join(dir, `chrome-profile-${process.pid}`);
console.log('Local verification server ready');
const chrome = spawn('C:/Program Files/Google/Chrome/Application/chrome.exe', ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--disable-background-networking', '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank'], { windowsHide: true, stdio: 'ignore' });
let ws;
let nextId = 0;
const pending = new Map();
const errors = [];
const calls = [];
const checks = [];
const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms));
async function eventually(fn, timeout = 20000) {
  const started = Date.now();
  while (Date.now() - started < timeout) {
    try { const value = await fn(); if (value) return value; } catch {}
    await delay(100);
  }
  throw new Error('Timed out waiting for browser state');
}
function send(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = ++nextId;
    const timer = setTimeout(() => { pending.delete(id); reject(new Error('CDP timeout: ' + method)); }, 20000);
    pending.set(id, { resolve: (value) => { clearTimeout(timer); resolve(value); }, reject: (error) => { clearTimeout(timer); reject(error); } });
    ws.send(JSON.stringify({ id, method, params }));
  });
}
async function evaluate(expression) {
  const response = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
  if (response.exceptionDetails) throw new Error(JSON.stringify(response.exceptionDetails));
  return response.result.value;
}
async function check(label, expression) {
  assert.equal(await evaluate(expression), true, label);
  checks.push(label);
  console.log('PASS ' + label);
}
async function navigate(route, role = 'SALE_STAFF', mode = '') {
  await send('Page.navigate', { url: `${baseUrl}${route}?role=${role}${mode ? '&mode=' + mode : ''}` });
  await eventually(() => evaluate('!!window.aiVerification && !!document.querySelector("h1,table,[role=dialog]")'));
  await delay(200);
}
async function screenshot(name) {
  const { data } = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  await fs.writeFile(path.join(dir, name + '.png'), Buffer.from(data, 'base64'));
}
try {
  const portInfo = await eventually(async () => await fs.readFile(path.join(profile, 'DevToolsActivePort'), 'utf8'));
  const port = portInfo.split('\n')[0];
  const targets = await (await fetch(`http://127.0.0.1:${port}/json/list`, { signal: AbortSignal.timeout(5000) })).json();
  ws = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl);
  ws.addEventListener('message', ({ data }) => {
    const message = JSON.parse(data);
    if (message.id) {
      const callback = pending.get(message.id);
      pending.delete(message.id);
      if (message.error) callback.reject(message.error); else callback.resolve(message.result);
    } else if (message.method === 'Runtime.exceptionThrown') errors.push(message.params.exceptionDetails);
    else if (message.method === 'Network.requestWillBeSent') calls.push(message.params.request.url);
  });
  await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }); });
  await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable');
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false });
  await navigate('/system/ai/reports');
  await check('SALE_STAFF opens AI Reports with pending backend and disabled export', `document.querySelector('h1').textContent === 'Báo cáo AI' && document.body.textContent.includes('đang chờ kết nối backend') && [...document.querySelectorAll('button')].find(b => b.textContent === 'Xuất báo cáo').disabled`);
  await check('SALE_STAFF SystemSidebar only exposes AI', `document.querySelector('aside nav').querySelectorAll(':scope > ul > li').length === 1 && document.querySelector('aside nav').textContent.includes('Báo cáo AI') && !document.querySelector('aside nav').textContent.includes('Đơn hàng')`);
  await check('Active parent stays open, active child and breadcrumb match', `document.querySelector('aside button[aria-expanded]').getAttribute('aria-expanded') === 'true' && document.querySelector('aside a[aria-current="page"]').textContent === 'Báo cáo AI' && document.querySelector('header nav').textContent.includes('AI›Báo cáo AI')`);
  await screenshot('sale-staff-ai-reports');
  await evaluate(`[...document.querySelectorAll('button')].find(b => b.textContent === 'Nhập báo cáo').click()`);
  await eventually(() => evaluate(`!!document.querySelector('[role="dialog"]')`));
  await check('Import dialog disabled, no persistence or false success', `document.querySelector('[role=dialog] button[type=submit]').disabled && document.querySelector('[role=dialog]').textContent.includes('chưa được lưu') && !document.body.textContent.includes('Đã nhập báo cáo.')`);
  await evaluate(`document.querySelector('[role=dialog] form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))`);
  await check('Import validates required metadata and file', `document.querySelectorAll('[role=dialog] [aria-invalid=true]').length === 3 && document.querySelector('[role=dialog]').textContent.includes('Vui lòng nhập tên báo cáo') && document.querySelector('[role=dialog]').textContent.includes('Vui lòng chọn một file')`);
  await evaluate(`{ const input=document.querySelector('input[type=file]'); const data=new DataTransfer(); data.items.add(new File(['bad'], 'bao-cao.exe', {type:'application/octet-stream'})); input.files=data.files; input.dispatchEvent(new Event('change',{bubbles:true})); }`);
  await check('File picker rejects unsupported file', `document.querySelector('[role=dialog]').textContent.includes('Chỉ hỗ trợ file PDF hoặc XLSX')`);
  await evaluate(`{ const target=document.querySelector('[role=dialog] input[type=file]').parentElement; const data=new DataTransfer(); data.items.add(new File(['%PDF-1.7'], 'bao-cao.pdf', {type:'application/pdf'})); target.dispatchEvent(new DragEvent('drop',{bubbles:true,cancelable:true,dataTransfer:data})); }`);
  await check('Drag/drop accepts PDF but import remains disabled', `document.querySelector('[role=dialog]').textContent.includes('bao-cao.pdf') && !document.querySelector('[role=dialog] input[type=file]').getAttribute('aria-invalid').includes('true') && document.querySelector('[role=dialog] button[type=submit]').disabled`);
  await screenshot('import-dialog');
  await evaluate(`window.dispatchEvent(new KeyboardEvent('keydown', {key:'Escape'}))`);
  await check('Escape closes dialog', `!document.querySelector('[role=dialog]')`);
  await check('Validation boundaries, extension, MIME and whitespace', `(()=>{const v=window.aiVerification; return !!v.validateAiReportFile(new File([], 'empty.pdf',{type:'application/pdf'})) && !!v.validateAiReportFile(new File([new Uint8Array(10*1024*1024+1)], 'large.xlsx',{type:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'})) && !v.validateAiReportFile(new File([new Uint8Array(10*1024*1024)], 'allowed.XLSX',{type:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'})) && !v.validateAiReportFile(new File(['data'], 'allowed.xlsx')) && !!v.validateAiReportFile(new File(['data'], 'bad.pdf',{type:'image/png'})) && !!v.validateAiReportFile(new File(['data'], 'bad.__proto__')) && !!v.validateAiReportFile(new File(['data'], 'pdf')) && Object.keys(v.validateAiReportMetadata({name:'   ',version:'\t'})).length === 2 && Object.keys(v.validateAiReportMetadata({name:'a'.repeat(201),version:'v'.repeat(51)})).length === 2 && Object.keys(v.validateAiReportMetadata({name:'  Báo cáo  ',version:'1.0'})).length === 0;})()`);
  await check('No business API, no changed global backoffice roles, POS entry exists', `window.aiVerification.getAiReportApi() === null && !window.aiVerification.BACKOFFICE_ROLES.includes('SALE_STAFF') && window.aiVerification.filterPosNavigationByRoles(window.aiVerification.posNavigationItems,['SALE_STAFF']).some(i=>i.key==='ai' && i.children[0].to==='/system/ai/reports')`);
  for (const route of ['/system', '/system/dashboard', '/system/orders', '/system/customers', '/system/products', '/system/marketing/events', '/system/inventory/balances', '/management/inventory/documents']) {
    await navigate(route);
    await check('SALE_STAFF denied ' + route, `document.body.textContent.includes('Bạn không có quyền truy cập') && !document.querySelector('aside')`);
  }
  await navigate('/system/ai/reports', 'CUSTOMER');
  await check('CUSTOMER denied AI Reports', `document.body.textContent.includes('Bạn không có quyền truy cập')`);
  await navigate('/system/ai/reports', 'ADMIN');
  await check('AI order is Marketing → AI → Báo cáo', `(()=>{const roots=[...document.querySelector('aside nav > ul').children].map(li=>li.querySelector('button,a').textContent); const i=roots.indexOf('AI'); return roots[i-1]==='Marketing' && roots[i+1]==='Báo cáo';})()`);
  await screenshot('admin-ai-reports');
  await evaluate(`[...document.querySelectorAll('aside button')].find(b=>b.textContent==='Marketing').click()`);
  await check('Inactive menu expands', `[...document.querySelectorAll('aside button')].find(b=>b.textContent==='Marketing').getAttribute('aria-expanded')==='true'`);
  await evaluate(`[...document.querySelectorAll('aside button')].find(b=>b.textContent==='Marketing').click()`);
  await check('Inactive menu collapses while active AI remains open', `[...document.querySelectorAll('aside button')].find(b=>b.textContent==='Marketing').getAttribute('aria-expanded')==='false' && [...document.querySelectorAll('aside button')].find(b=>b.textContent==='AI').getAttribute('aria-expanded')==='true'`);
  for (const role of ['MANAGER','SUPER_ADMIN','INVENTORY_STAFF','QC','ACCOUNTANT']) {
    await navigate('/system/ai/reports',role);
    await check(role + ' opens AI Reports', `document.querySelector('h1').textContent==='Báo cáo AI'`);
  }
  await navigate('/system/ai/reports','SALE_STAFF','loading');
  await check('Loading state renders', `document.querySelector('table').parentElement.parentElement.getAttribute('aria-busy')==='true' && document.querySelectorAll('.animate-pulse').length===5`);
  await navigate('/system/ai/reports','SALE_STAFF','error');
  await check('Error and retry render', `document.body.textContent.includes('Không thể tải danh sách báo cáo') && [...document.querySelectorAll('button')].some(b=>b.textContent==='Thử lại')`);
  await evaluate(`[...document.querySelectorAll('button')].find(b=>b.textContent==='Thử lại').click()`);
  await check('Retry invokes callback', `document.body.dataset.retried==='true'`);
  await navigate('/system/ai/reports','SALE_STAFF','empty');
  await check('Connected empty state renders', `document.body.textContent.includes('Chưa có dữ liệu báo cáo')`);
  await navigate('/system/ai/reports','SALE_STAFF','row');
  await check('Row shows business metadata, original-file actions disabled without backend', `document.body.textContent.includes('Báo cáo kiểm tra giao diện') && document.body.textContent.includes('1.0') && document.querySelector('button[aria-label^="Xem"]').disabled && document.querySelector('button[aria-label^="Xuất"]').disabled && !document.querySelector('button[aria-label^="Chỉnh sửa"]').disabled && !document.body.textContent.includes('fixture-file')`);
  await navigate('/system/ai/reports','SALE_STAFF','edit');
  await check('Edit only offers name/version and disables save', `document.querySelectorAll('[role=dialog] input').length===2 && !document.querySelector('[role=dialog] input[type=file]') && document.querySelector('[role=dialog] button[type=submit]').disabled`);
  await evaluate(`document.querySelectorAll('[role=dialog] input').forEach(input=>{Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,'   ');input.dispatchEvent(new Event('input',{bubbles:true}));});`);
  await evaluate(`document.querySelector('[role=dialog] form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}))`);
  await check('Edit validates whitespace-only metadata', `document.querySelectorAll('[role=dialog] [aria-invalid=true]').length===2`);
  await screenshot('edit-dialog-validation');
  await navigate('/system/ai/reports');
  await send('Emulation.setDeviceMetricsOverride', { width: 1024, height: 768, deviceScaleFactor: 1, mobile: false });
  await check('1024px viewport has no page horizontal overflow', `document.documentElement.scrollWidth <= window.innerWidth`);
  await screenshot('ai-reports-1024');
  assert.equal(errors.length, 0, JSON.stringify(errors));
  const apiCalls = calls.filter(url=>/localhost:8080|\/api\/|\/file\/|\/catalog\/|\/recommendations\//.test(url) && !url.includes('/src/'));
  assert.deepEqual(apiCalls, [], 'No backend calls should run for pending AI report UI');
  await fs.writeFile(path.join(dir,'results.json'), JSON.stringify({checks, consoleErrors:errors, backendCalls:apiCalls, fixtureNote:'Rows/edit/async states verified only in this isolated harness; production has no fixtures or mock API.'},null,2));
  console.log('TOTAL ' + checks.length + ' CHECKS PASSED; zero runtime errors; zero backend calls.');
} catch (error) {
  console.error('VERIFICATION FAILED', error, JSON.stringify(errors));
  if (ws?.readyState === 1) console.error('BROWSER STATE', await evaluate('({title:document.title,text:document.body.innerText.slice(0,2000),verification:!!window.aiVerification})'));
  process.exitCode = 1;
} finally {
  ws?.close();
  chrome.kill();
  await Promise.race([server.close(), delay(2000)]);
  process.exit(process.exitCode || 0);
}
