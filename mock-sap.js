// Mock do SAP Business One Service Layer (sem dependências). Uso: node mock-sap.js
// Sobe em http://localhost:50000. MOCK_FAIL=1 faz os POSTs de documento falharem (testa o reenvio).
const http = require('http');
const { URL } = require('url');

const PORT = 50000;
const SESSION = 'MOCK-' + Date.now();
const docs = { InventoryGenExits: [], InventoryGenEntries: [] };
let nextDocEntry = 1;

function send(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(body === undefined ? '' : JSON.stringify(body));
}

function readBody(req) {
  return new Promise((resolve) => {
    let data = '';
    req.on('data', (c) => (data += c));
    req.on('end', () => {
      try { resolve(data ? JSON.parse(data) : {}); } catch { resolve(null); }
    });
  });
}

function sapError(res, status, code, msg) {
  send(res, status, { error: { code, message: { lang: 'en-us', value: msg } } });
}

http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  const path = url.pathname;
  console.log(`${new Date().toISOString()} ${req.method} ${path}${url.search}`);

  if (req.method === 'POST' && path === '/b1s/v1/Login') {
    const body = await readBody(req);
    if (!body || !body.CompanyDB || !body.UserName || !body.Password) {
      return sapError(res, 400, -1, 'Login inválido: CompanyDB, UserName e Password são obrigatórios');
    }
    res.setHeader('Set-Cookie', [`B1SESSION=${SESSION}; path=/; HttpOnly`, 'ROUTEID=.node1; path=/b1s']);
    console.log(`  login ok (CompanyDB=${body.CompanyDB}, user=${body.UserName})`);
    return send(res, 200, { SessionId: SESSION, Version: '1000000', SessionTimeout: 30 });
  }

  const cookie = req.headers['cookie'] || '';
  if (!cookie.includes(`B1SESSION=${SESSION}`)) {
    console.log('  401: sessão inválida');
    return sapError(res, 401, 301, 'Invalid session.');
  }

  if (req.method === 'POST' && path === '/b1s/v1/Logout') {
    return send(res, 204);
  }

  const m = path.match(/^\/b1s\/v1\/(InventoryGenExits|InventoryGenEntries)$/);
  if (!m) return sapError(res, 404, -1, 'Recurso não encontrado: ' + path);
  const entity = m[1];

  if (req.method === 'GET') {
    const filtro = url.searchParams.get('$filter') || '';
    const ref = (filtro.match(/Reference2 eq '(.*)'/) || [])[1];
    const achados = docs[entity].filter((d) => ref && d.Reference2 === ref).map((d) => ({ DocEntry: d.DocEntry }));
    return send(res, 200, { value: achados });
  }

  if (req.method === 'POST') {
    if (process.env.MOCK_FAIL === '1') return sapError(res, 500, -2028, 'Falha simulada (MOCK_FAIL=1)');
    const doc = await readBody(req);
    if (!doc || !Array.isArray(doc.DocumentLines) || doc.DocumentLines.length === 0) {
      return sapError(res, 400, -10, 'DocumentLines é obrigatório');
    }
    for (const l of doc.DocumentLines) {
      if (!l.ItemCode || !(l.Quantity > 0)) return sapError(res, 400, -10, 'Linha inválida: ItemCode/Quantity');
    }
    const salvo = { DocEntry: nextDocEntry++, ...doc };
    docs[entity].push(salvo);
    console.log(`  ${entity} criado, DocEntry=${salvo.DocEntry}, Reference2=${doc.Reference2}`);
    console.log(JSON.stringify(doc, null, 2).split('\n').map((x) => '    ' + x).join('\n'));
    return send(res, 201, salvo);
  }

  return sapError(res, 405, -1, 'Método não suportado');
}).listen(PORT, () => console.log(`Mock SAP B1 Service Layer em http://localhost:${PORT}`));
