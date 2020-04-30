'use strict';

var os = require('os');
var nodeStatic = require('node-static');
var https = require('http'); // use require('https') for https
var socketIO = require('socket.io');
var fs = require("fs");
var options = {
  // for https:
  //key: fs.readFileSync('key.pem'),
  //cert: fs.readFileSync('cert.pem')
};

var fileServer = new(nodeStatic.Server)();
var app = https.createServer(options,function(req, res) {
  fileServer.serve(req, res);

}).listen(1794, "0.0.0.0", function(){
  console.log('listening to request on port 1794');
});

var io = socketIO.listen(app);
io.sockets.on('connection', function(socket) {

  // convenience function to log server messages on the client
  function log() {
    var array = ['Message from server:'];
    array.push.apply(array, arguments);
    socket.emit('log', array);
  }

  socket.on('message', function(message, room) {
    log('Client said: ', message);
    console.log('Client said: ' + message + ' in room: ' + room);

    // for a real app, would be room-only (not broadcast)
    console.log('Echo to Client: ' + message + ' in room: ' + room);
    //io.to(room).emit('message', message, room);
    socket.broadcast.emit('message', message);
  });

  socket.on('join', function(room, client_id){
    log('Received request to join room ' + room + ' with Client ID ' + client_id);
    console.log('Received request to join room ' + room + ' with Client ID ' + client_id);

    var clientsInRoom = io.sockets.adapter.rooms[room];
    var numClients = clientsInRoom ?Object.keys(clientsInRoom.sockets).length : 0;
    //var numClients = io.sockets.sockets.length;
    log('Room ' + room + ' now has ' + numClients + ' client(s)');
    console.log('Room ' + room + ' now has ' + numClients + ' client(s)');

    joinAck(room, numClients);
  });

  socket.on('create or join', function(room) {
    log('Received request to create or join room ' + room);
    console.log('Received request to create or join room ' + room);

    var clientsInRoom = io.sockets.adapter.rooms[room];
    var numClients = clientsInRoom ?Object.keys(clientsInRoom.sockets).length : 0;
    //var numClients = io.sockets.sockets.length;
    log('Room ' + room + ' now has ' + numClients + ' client(s)');
    console.log('Room ' + room + ' now has ' + numClients + ' client(s)');

    if (numClients === 0) {
      socket.join(room);
      log('Client ID ' + socket.id + ' created room ' + room);
      console.log('Client ID ' + socket.id + ' created room ' + room);
      socket.emit('created', room, socket.id);

      log('Room ' + room + ' now has 1 client');
      console.log('Room ' + room + ' now has 1 client');
    } else {
      joinAck(room, numClients);
    }
  });

  function joinAck(room, numClients) {
    if (numClients <= 2) {
      log('Client ID ' + socket.id + ' joined room ' + room);
      console.log('Client ID ' + socket.id + ' joined room ' + room);
      io.sockets.in(room).emit('join', room, socket.id);
      socket.join(room);
      socket.emit('joined', room, socket.id);
      io.sockets.in(room).emit('ready');

      var clientsInRoom = io.sockets.adapter.rooms[room];
      numClients = clientsInRoom ?Object.keys(clientsInRoom.sockets).length : 0;
      log('Room ' + room + ' now has ' + numClients + ' client(s)');
      console.log('Room ' + room + ' now has ' + numClients + ' client(s)');
    } else { // max 3 clients
      socket.emit('full', room);
    }
  }

  socket.on('ipaddr', function() {
    var ifaces = os.networkInterfaces();
    for (var dev in ifaces) {
      ifaces[dev].forEach(function(details) {
        if (details.family === 'IPv4' && details.address !== '127.0.0.1') {
          socket.emit('ipaddr', details.address);
        }
      });
    }
  });

  socket.on('bye', function(room, client_id) {
    console.log('received bye from ClientID: ' + client_id + ', in room: ' + room);
  });
});
