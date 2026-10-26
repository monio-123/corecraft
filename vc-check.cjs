const fs = require('fs');
const { parse, compileTemplate } = require('@vue/compiler-sfc');
const src = fs.readFileSync('src/views/corecraft-web/AiAdoptDialog.vue', 'utf8');
const { descriptor, errors } = parse(src, { filename: 'AiAdoptDialog.vue' });
if (errors.length) { console.log('PARSE ERRORS:', errors.map(e => e.message + ' @line ' + (e.loc && e.loc.start.line))); }
try {
  const r = compileTemplate({ source: descriptor.template.content, filename: 'AiAdoptDialog.vue', id: 'x' });
  console.log('TEMPLATE OK, errors=', r.errors.length);
  r.errors.forEach(e => console.log('  -', e.message, '@line', e.loc && e.loc.start.line));
} catch (e) { console.log('THROWN:', e.message); }
