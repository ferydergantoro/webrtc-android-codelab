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

  socket.on('offer', function(message_offer, room, client_id, from_client_id, peerkey){
    log('Offer ' + from_client_id + ' to client ' + client_id + ' in room ' + room);
    console.log('Offer ' + from_client_id + ' to client ' + client_id + ' in room ' + room);

    //var arraySockets = Object.keys(io.sockets.in(room).clients().sockets);

    io.to(client_id).emit('offer', message_offer, room, client_id, from_client_id, peerkey/*, arraySockets*/);
  });

  socket.on('answer', function(message_offer, room, client_id, from_client_id, peerkey){
    log('Answer ' + from_client_id + ' to client ' + client_id + ' in room ' + room);
    console.log('Answer ' + from_client_id + ' to client ' + client_id + ' in room ' + room);

    //var arraySockets = Object.keys(io.sockets.in(room).clients().sockets);

    io.to(client_id).emit('answer', message_offer, room, client_id, from_client_id, peerkey/*, arraySockets*/);
  });

  socket.on('icecandidate', function(message_ice, room, client_id, from_client_id, peerkey){
    log('Ice Candidate ' + from_client_id + ' to client ' + client_id + ' in room ' + room);
    console.log('Ice Candidate ' + from_client_id + ' to client ' + client_id + ' in room ' + room);

    //var arraySockets = Object.keys(io.sockets.in(room).clients().sockets);

    io.to(client_id).emit('icecandidate', message_ice, room, client_id, from_client_id, peerkey/*, arraySockets*/);
  });

  socket.on('connected', function(room, client_id, from_client_id){
    log('Client ' + from_client_id + ' have connected to client ' + client_id + ' in room ' + room);
    console.log('Client ' + from_client_id + ' have connected to client ' + client_id + ' in room ' + room);

    var arraySockets = Object.keys(io.sockets.in(room).clients().sockets);

    io.sockets.in(room).emit('connected', room, client_id, from_client_id, arraySockets);
  });

  socket.on('message', function(message, room, client_id, from_client_id, peerkey) {
    log('Client said: ', message);
    console.log('Client '+ client_id +' said: ' + message + ' in room: ' + room);

    // for a real app, would be room-only (not broadcast)
    console.log('Echo to all Client: ' + message + ' in room: ' + room + ' from client: ' + client_id);
    
    //socket.broadcast.emit('message', message, room, client_id);
    io.sockets.in(room).emit('message', message, room, client_id, from_client_id, peerkey);
  });

  socket.on('join', function(room, client_id){
    log('Received request to join room ' + room + ' with Client ID ' + client_id);
    console.log('Received request to join room ' + room + ' with Client ID ' + client_id);

    var clientsInRoom = io.sockets.adapter.rooms[room];
    var numClients = clientsInRoom ? Object.keys(clientsInRoom.sockets).length : 0;

    log('Room ' + room + ' now has ' + numClients + ' client(s)');
    console.log('Room ' + room + ' now has ' + numClients + ' client(s)');

    joinAck(room, numClients);
  });

  socket.on('create or join', function(room) {
    log('Received request to create or join room ' + room);
    console.log('Received request to create or join room ' + room);

    var clientsInRoom = io.sockets.adapter.rooms[room];
    var numClients = clientsInRoom ?Object.keys(clientsInRoom.sockets).length : 0;

    log('Room ' + room + ' now has ' + numClients + ' client(s)');
    console.log('Room ' + room + ' now has ' + numClients + ' client(s)');

    if (numClients === 0) {
      socket.join(room);
      log('Client ID ' + socket.id + ' created room ' + room);
      console.log('Client ID ' + socket.id + ' created room ' + room);
      
      var arraySockets = Object.keys(io.sockets.in(room).clients().sockets);
      console.log('arraySockets: ' + arraySockets);

      var clientsInRoom = io.sockets.adapter.rooms[room];
      numClients = clientsInRoom ? Object.keys(clientsInRoom.sockets).length : 0;

      socket.emit('created', room, socket.id, numClients, arraySockets);

      log('Room ' + room + ' now has 1 client');
      console.log('Room ' + room + ' now has 1 client');
    } else {
      joinAck(room, numClients);
    }
  });

  function joinAck(room, numClients) {
    //if (numClients <= 2) {
      log('Client ID ' + socket.id + ' joined room ' + room);
      console.log('Client ID ' + socket.id + ' joined room ' + room);
      
      socket.join(room);

      var clientsInRoom = io.sockets.adapter.rooms[room];
      var arraySockets = Object.keys(io.sockets.in(room).clients().sockets);
      console.log('Clients are: ' + arraySockets);
      
      numClients = clientsInRoom ? Object.keys(clientsInRoom.sockets).length : 0;
      
      socket.emit('joined', room, socket.id, numClients, arraySockets);
      //io.sockets.in(room).emit('joined', room, socket.id, numClients, arraySockets);
      io.sockets.in(room).emit('ready');

      io.sockets.in(room).emit('join', room, socket.id, arraySockets.length, arraySockets);

      log('Room ' + room + ' now has ' + numClients + ' client(s)');
      console.log('Room ' + room + ' now has ' + numClients + ' client(s)');
    //} else { // max 3 clients
    //  socket.emit('full', room);
    //}
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
    var message = 'bye';
    console.log('received bye from ClientID: ' + client_id + ', in room: ' + room);
    console.log('Emit to all Client: ' + message + ' in room: ' + room + ' from client: ' + client_id);

    var arraySockets = Object.keys(io.sockets.in(room).clients().sockets);
    arraySockets = arraySockets.remove(client_id);

    //socket.broadcast.emit('message', message, room, client_id);
    socket.to(room).emit('message', message, room, client_id, arraySockets.length, arraySockets);
  });

  Array.prototype.remove = function() {
    var what, a = arguments, L = a.length, ax;
    while (L && this.length) {
        what = a[--L];
        while ((ax = this.indexOf(what)) !== -1) {
            this.splice(ax, 1);
        }
    }
    return this;
  };
});
