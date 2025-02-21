const WebSocket = require('ws');

const server = new WebSocket.Server({ port: 8080 });
server.on('connection', function connection(connection) {
    connection.on('message', function incoming(message) {
        connection.send("i am the server");
    });
});

const client = new WebSocket('ws://localhost:9999');
client.on('open', function open() {
    client.send('i am the client');
});

client.on('message', function incoming(message) {
    console.log("Finished");
    process.exit();
});