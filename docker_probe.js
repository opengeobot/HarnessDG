// Probe docker_engine named pipe via HTTP-over-npipe (same mechanism as docker CLI)
var net = require('net');
var pipe = process.argv[2] || '\\\\.\\pipe\\docker_engine';
var path = process.argv[3] || '/v1.44/version';
var client = net.connect(pipe, function () {
  client.write('GET ' + path + ' HTTP/1.1\r\nHost: npipe\r\nConnection: close\r\n\r\n');
});
var buf = '';
client.on('data', function (d) { buf += d.toString('utf8'); });
client.on('error', function (e) { console.log('PIPE_ERROR: ' + e.message); process.exit(2); });
client.on('close', function () {
  var idx = buf.indexOf('\r\n\r\n');
  var body = idx >= 0 ? buf.substring(idx + 4) : buf;
  console.log('RAW_LEN: ' + buf.length);
  console.log(body.substring(0, 2000));
  process.exit(0);
});
setTimeout(function () { console.log('TIMEOUT: no response from engine'); process.exit(3); }, 15000);
