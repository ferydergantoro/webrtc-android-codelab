'use strict';

var connections = {};

Object.size = function(obj) {
  var size = 0, key;
  for (key in obj) {
      if (obj.hasOwnProperty(key)) size++;
  }
  return size;
};

var isChannelReady = false;
var isInitiator = false;
var isStarted = false;
var localStream;
var pc;
var pc2;
var remoteStream;
var turnReady;

var pcConfig = {
  'iceServers': [
    //{
      //'url': 'stun:stun.l.google.com:19302'
    //},
    {
      'url' : 'turn:202.51.110.214:8282',
      'username' : 'eluon',
      'credential' : 'eluon123'
    }
  ]
};

// Set up audio and video regardless of what devices are present.
var sdpConstraints = {
  'mandatory': {
    'OfferToReceiveAudio': true,
    'OfferToReceiveVideo': true
  }
};

/////////////////////////////////////////////

// Could prompt for room name:
var room = prompt('Enter room name:', 'eluon');
var clientID = null;

if (room === '') {
  room = 'eluon';
}

var localVideo = document.querySelector('#localVideo');
var remoteVideo = document.querySelector('#remoteVideo');
var remoteVideo2 = document.querySelector('#remoteVideo2');

var socket = io.connect("http://127.0.0.1:1794");
//var socket = io.connect("http://ns2.eluon.co.id:8282");

navigator.mediaDevices.getUserMedia({
  audio: true,
  video: true
})
.then(gotStream)
.catch(function(e) {
  alert('getUserMedia() error: ' + e.name);
});

function gotStream(stream) {
  trace('Requesting local stream.');
  console.log('Adding local stream.');
  if ('srcObject' in localVideo) {
    localVideo.srcObject = stream;
  } else {
    // deprecated
    localVideo.src = window.URL.createObjectURL(stream);
  }
  localStream = stream;
  trace('Received local stream.');

  socket.emit('create or join', room);
  console.log('Attempted to create or join room', room);

  if (isInitiator) {
    maybeStart(clientID);
  }
}

var constraints = {
  video: true,
  audio: true
};

console.log('Getting user media with constraints', constraints);

if (location.hostname !== 'localhost') {
 // requestTurn(
//    'https://computeengineondemand.appspot.com/turn?username=41784574&key=4080218913'
   // 'https://service.xirsys.com/ice?ident=vivekchanddru&secret=ad6ce53a-e6b5-11e6-9685-937ad99985b9&domain=www.vivekc.xyz&application=default&room=testing&secure=1'
//);
}

////////////////////////////////////////////////////

socket.on('created', function(room, client_id, numClients, clients) {
  console.log('Created room: ' + room + '('+numClients+' Clients)' + ', Client ID: ' + client_id);
  console.log('All members: ' + clients);
  clientID = client_id;
  isInitiator = true;
  if (typeof localStream !== 'undefined') {
    sendMessage('got user media', clientID);
  }
});

socket.on('full', function(room) {
  console.log('Room ' + room + ' is full');
});

socket.on('join', function (room, client_id, numClients, clients){
  console.log('Another peer (Client ID: ' + client_id + ') made a request to join room ' + room + '('+numClients+' Clients)');
  console.log('All members: ' + clients);
  if (isInitiator) {
    console.log('This peer is the initiator of room ' + room + '!');
  }
  isChannelReady = true;
  /*if (clientID != client_id) {
    maybeStart(client_id);
  }*/
});

socket.on('joined', function(room, client_id, numClients, clients) {
  console.log('Client ID: ' + client_id + ' has been joined in room: ' + room + '('+numClients+' Clients)');
  console.log('All members: ' + clients);
  isChannelReady = true;
  if (clientID == null) {
    clientID = client_id;
  }
  /*if (clientID == client_id) {
    maybeStart(client_id);
  }*/
  if (typeof localStream !== 'undefined') {
    sendMessage('got user media', clientID);
  }
});

socket.on('log', function(array) {
  console.log.apply(console, array);
});

////////////////////////////////////////////////

function sendMessage(message, client_id) {
  console.log('Client sending message: ', message);
  socket.emit('message', message, room, client_id);
}

function sendRequestJoin(room, client_id) {
  console.log('Client sending request join with room: ' + room + ", Client ID: " + client_id);
  if (!isChannelReady && clientID != null && clientID != ''){
    socket.emit('join', room, client_id);
  }
}

// This client receives a message
socket.on('message', function(message, room, client_id) {
  console.log('Client received message:', message);

  if (message === 'got user media') {
    if (isChannelReady) {
      maybeStart(client_id);
    } else if (clientID != null && clientID != ''){
      sendRequestJoin(room, clientID);
    }
  } else if (message.type === 'offer') {
    console.log("Received offer client " + client_id);
    if (!isInitiator && !isStarted) {
      maybeStart(client_id);
    }
    var currPc = connections[client_id];
    if (currPc) {
      console.log("setRemoteDescription client " + client_id);
      currPc.setRemoteDescription(new RTCSessionDescription(message));  
      doAnswer(currPc, client_id);
    }
  } else if (message.type === 'answer' && isStarted) {
    console.log("received answer");
    var currPc = connections[client_id];
    if (currPc) {
      currPc.setRemoteDescription(new RTCSessionDescription(message));
    }
  } else if (message.type === 'candidate' && isStarted) {
    var candidate = new RTCIceCandidate({
      sdpMLineIndex: message.label,
      candidate: message.candidate
    });
    var currPc = connections[client_id];
    if (currPc) {
      currPc.addIceCandidate(candidate);
    }
  } else if (message === 'bye' && isStarted) {
    handleRemoteHangup(client_id);
  }
});

/////////////////////////////////////////////////////////

function maybeStart(client_id) {
  console.log('>>>>>>> maybeStart() ', isStarted, localStream, isChannelReady);
  if ((isInitiator || !isStarted || clientID != client_id) && typeof localStream !== 'undefined') {
    if (isChannelReady && clientID != null && typeof clientID !== 'undefined') {
      var currPc = null;
      if (!isStarted || clientID != client_id) {
        if (!isStarted ) {
          console.log('>>>>>> creating peer connection');
          currPc = createPeerConnection(clientID);
        } else if (clientID != client_id) {
          delete connections[null];
          currPc = connections[client_id];
          if (currPc) {
            //
          } else {
            currPc = createPeerConnection(client_id);
          }
        }
      }
      if (currPc != null && client_id != null) {
        // Add local stream to connection and create offer to connect.
        currPc.addStream(localStream);
        trace('Added local stream to localPeerConnection.');
        isStarted = true;
        console.log('isInitiator', isInitiator);
        if (isInitiator && clientID != null && clientID != client_id) {
          doCall(currPc, client_id);
        }
      }
    } else {
        sendRequestJoin(room, clientID);
    }
  }
}

window.onbeforeunload = function() {
  sendMessage('bye', this.clientID);
};

/////////////////////////////////////////////////////////

function createPeerConnection(client_id) {
  trace('Starting create peer connection');

  // // Get local media stream tracks.
  // const videoTracks = localStream.getVideoTracks();
  // const audioTracks = localStream.getAudioTracks();
  // if (videoTracks.length > 0) {
  //   trace(`Using video device: ${videoTracks[0].label}.`);
  // }
  // if (audioTracks.length > 0) {
  //   trace(`Using audio device: ${audioTracks[0].label}.`);
  // }

  const servers = null;  // Allows for RTC server configuration.
  try {
    var currPc = null;
    var isNew = false;
    var isSecond = false;
    if (!pc || typeof pc === 'undefined') {
      pc = new RTCPeerConnection(servers);
      trace('Created local peer connection object localPeerConnection.');
      isNew = true;
      connections[clientID] = pc;
      if (clientID != client_id) {
        connections[client_id] = pc;
      }
      currPc = pc;
    }
    
    if (currPc == null) {
      if (pc) {
        connections[clientID] = pc;
      }
      currPc = connections[client_id];
      var sizeConn = Object.size(connections);
      if (currPc) {
        //
      } else if (sizeConn == 1) {
        isNew = true;
        connections[clientID] = pc;
        if (isInitiator) {
          connections[client_id] = pc;
        } else {
          if (!pc2 || typeof pc2 === 'undefined') {
            pc2 = new RTCPeerConnection(servers);
            trace('Created local peer connection object localPeerConnection.');
          }  
          isSecond = true;
          connections[client_id] = pc2;
        }
        currPc = connections[client_id];
      } else if (sizeConn == 2 && isInitiator) {
        isNew = true;
        if (!pc2 || typeof pc2 === 'undefined') {
          pc2 = new RTCPeerConnection(servers);
          trace('Created local peer connection object localPeerConnection.');
        }
        isSecond = true;
        connections[client_id] = pc2;
        currPc = connections[client_id];
      }
    }
    
    if (currPc != null || isNew) {
      currPc.addEventListener('icecandidate', handleConnection);
      currPc.addEventListener('iceconnectionstatechange', handleConnectionChange);

      //currPc.onicecandidate = handleIceCandidate;
      currPc.onicecandidate = function (event) {
        console.log('icecandidate event: ', event);
        if (event.candidate) {
          sendMessage({
            type: 'candidate',
            label: event.candidate.sdpMLineIndex,
            id: event.candidate.sdpMid,
            candidate: event.candidate.candidate
          }, client_id);
        } else {
          console.log('End of candidates.');
        }
      };
      if ('ontrack' in currPc) {
        currPc.ontrack = isSecond ? handleRemoteStreamAdded2 : handleRemoteStreamAdded;
      } else {
        // deprecated
        currPc.onaddstream = isSecond ? handleRemoteStreamAdded2 : handleRemoteStreamAdded;
      }
      currPc.onremovestream = handleRemoteStreamRemoved;
      console.log('Created RTCPeerConnnection');
    }

    return currPc;
  } catch (e) {
    console.log('Failed to create PeerConnection, exception: ' + e.message);
    alert('Cannot create RTCPeerConnection object.');
    return null;
  }
}

// Define RTC peer connection behavior.

// Logs error when setting session description fails.
function setSessionDescriptionError(error) {
  trace(`Failed to create session description: ${error.toString()}.`);
}

// Logs success when setting session description.
function setDescriptionSuccess(peerConnection, functionName) {
  const peerName = getPeerName(peerConnection);
  trace(`${peerName} ${functionName} complete.`);
}

// Logs success when localDescription is set.
function setLocalDescriptionSuccess(peerConnection) {
  setDescriptionSuccess(peerConnection, 'setLocalDescription');
}

// Logs success when remoteDescription is set.
function setRemoteDescriptionSuccess(peerConnection) {
  setDescriptionSuccess(peerConnection, 'setRemoteDescription');
}

// function handleIceCandidate(event) {
//   console.log('icecandidate event: ', event);
//   if (event.candidate) {
//     sendMessage({
//       type: 'candidate',
//       label: event.candidate.sdpMLineIndex,
//       id: event.candidate.sdpMid,
//       candidate: event.candidate.candidate
//     }, );
//   } else {
//     console.log('End of candidates.');
//   }
// }

function handleRemoteStreamAdded2(event) {
  console.log('Remote 2nd stream added.');
  if ('srcObject' in remoteVideo2 || remoteVideo2.srcObject !== event.streams[0]) {
    remoteVideo2.srcObject = event.streams[0];
  } else {
    // deprecated
    remoteVideo2.src = window.URL.createObjectURL(event.stream);
  }
  remoteVideo2 = event.stream;
}

function handleRemoteStreamAdded(event) {
  console.log('Remote stream added.');
  if ('srcObject' in remoteVideo || remoteVideo.srcObject !== event.streams[0]) {
    remoteVideo.srcObject = event.streams[0];
  } else {
    // deprecated
    remoteVideo.src = window.URL.createObjectURL(event.stream);
  }
  remoteStream = event.stream;
}

function handleCreateOfferError(event) {
  console.log('createOffer() error: ', event);
}

// Connects with new peer candidate.
function handleConnection(event) {
  const peerConnection = event.target;
  const iceCandidate = event.candidate;

  if (iceCandidate) {
    const newIceCandidate = new RTCIceCandidate(iceCandidate);
    const otherPeer = getOtherPeer(peerConnection);

    if (otherPeer != null) {
      otherPeer.addIceCandidate(newIceCandidate)
        .then(() => {
          handleConnectionSuccess(peerConnection);
        }).catch((error) => {
          handleConnectionFailure(peerConnection, error);
        });

      trace(`${getPeerName(peerConnection)} ICE candidate:\n` +
            `${event.candidate.candidate}.`);
    }
  }
}

// Logs that the connection succeeded.
function handleConnectionSuccess(peerConnection) {
  trace(`${getPeerName(peerConnection)} addIceCandidate success.`);
};

// Logs that the connection failed.
function handleConnectionFailure(peerConnection, error) {
  trace(`${getPeerName(peerConnection)} failed to add ICE Candidate:\n`+
        `${error.toString()}.`);
}

// Logs changes to the connection state.
function handleConnectionChange(event) {
  const peerConnection = event.target;
  console.log('ICE state change event: ', event);
  trace(`${getPeerName(peerConnection)} ICE state: ` +
        `${peerConnection.iceConnectionState}.`);
}

function doCall(currPc, client_id) {
  console.log('Sending offer to peer to' + client_id);
  currPc.createOffer(
    //setLocalAndSendMessage, 
    function(sessionDescription){
      currPc.setLocalDescription(sessionDescription)
      .then(() => {
        setLocalDescriptionSuccess(currPc);
      }).catch(setSessionDescriptionError);

      console.log('setLocalAndSendMessage sending message-'+client_id, sessionDescription);
      sendMessage(sessionDescription, client_id);
    },
    handleCreateOfferError);
}

function doAnswer(currPc, client_id) {
  trace('createAnswer start to ' + client_id);
  console.log('Sending answer to peer-' + client_id);
  currPc.createAnswer().then(
    //setLocalAndSendMessage,
    function(sessionDescription){
      currPc.setLocalDescription(sessionDescription)
      .then(() => {
        setLocalDescriptionSuccess(currPc);
      }).catch(setSessionDescriptionError);

      console.log('setLocalAndSendMessage sending message-'+client_id, sessionDescription);
      sendMessage(sessionDescription, client_id);
    },
    setSessionDescriptionError
  );
}

// function setLocalAndSendMessage(sessionDescription) {
//   trace(`Offer from localPeerConnection:\nclient_id: ${sessionDescription.client_id}\nsdp: ${sessionDescription.sdp}`);

//   // Set Opus as the preferred codec in SDP if Opus is present.
//   //  sessionDescription.sdp = preferOpus(sessionDescription.sdp);
//   trace('localPeerConnection setLocalDescription start.');
//   var cid = `${sessionDescription.client_id}`;
//   if (!cid || typeof cid === 'undefined') {
//     cid = clientID;
//   }
//   var currPc = connections[cid];
//   if (currPc) {
//     currPc.setLocalDescription(sessionDescription)
//     .then(() => {
//       setLocalDescriptionSuccess(currPc);
//     }).catch(setSessionDescriptionError);
//   }
  
//   console.log('setLocalAndSendMessage sending message', sessionDescription);
//   sendMessage(sessionDescription, clientID);
// }

function requestTurn(turnURL) {
  var turnExists = false;
  for (var i in pcConfig.iceServers) {
    if (pcConfig.iceServers[i].url.substr(0, 5) === 'turn:') {
      turnExists = true;
      turnReady = true;
      break;
    }
  }
  if (!turnExists) {
    console.log('Getting TURN server from ', turnURL);
    // No TURN server. Get one from computeengineondemand.appspot.com:
    var xhr = new XMLHttpRequest();
    xhr.onreadystatechange = function() {
      if (xhr.readyState === 4 && xhr.status === 200) {
        var turnServer = JSON.parse(xhr.responseText);
        console.log('Got TURN server: ', turnServer);
        pcConfig.iceServers.push({
          'url': 'turn:' + turnServer.username + '@' + turnServer.turn,
          'credential': turnServer.password
        });
        turnReady = true;
      }
    };
    xhr.open('GET', turnURL, true);
    xhr.send();
  }
}

function handleRemoteStreamRemoved(event) {
  console.log('Remote stream removed. Event: ', event);
}

function hangup() {
  console.log('Hanging up.');
  sendMessage('bye', clientID);
  clientID = null;
  stop();
}

function handleRemoteHangup(client_id) {
  console.log('Session terminated.');
  delete connections[client_id];
  //clientID = null;
  //stop();
  //isInitiator = false;
}

function stop() {
  clientID = null;
  isStarted = false;
  // isAudioMuted = false;
  // isVideoMuted = false;
  pc.close();
  pc = null;
  pc2.close();
  pc2 = null;
}

///////////////////////////////////////////

// Set Opus as the default audio codec if it's present.
function preferOpus(sdp) {
  var sdpLines = sdp.split('\r\n');
  var mLineIndex;
  // Search for m line.
  for (var i = 0; i < sdpLines.length; i++) {
    if (sdpLines[i].search('m=audio') !== -1) {
      mLineIndex = i;
      break;
    }
  }
  if (mLineIndex === null) {
    return sdp;
  }

  // If Opus is available, set it as the default in m line.
  for (i = 0; i < sdpLines.length; i++) {
    if (sdpLines[i].search('opus/48000') !== -1) {
      var opusPayload = extractSdp(sdpLines[i], /:(\d+) opus\/48000/i);
      if (opusPayload) {
        sdpLines[mLineIndex] = setDefaultCodec(sdpLines[mLineIndex],
          opusPayload);
      }
      break;
    }
  }

  // Remove CN in m line and sdp.
  sdpLines = removeCN(sdpLines, mLineIndex);

  sdp = sdpLines.join('\r\n');
  return sdp;
}

function extractSdp(sdpLine, pattern) {
  var result = sdpLine.match(pattern);
  return result && result.length === 2 ? result[1] : null;
}

// Set the selected codec to the first in m line.
function setDefaultCodec(mLine, payload) {
  var elements = mLine.split(' ');
  var newLine = [];
  var index = 0;
  for (var i = 0; i < elements.length; i++) {
    if (index === 3) { // Format of media starts from the fourth.
      newLine[index++] = payload; // Put target payload to the first.
    }
    if (elements[i] !== payload) {
      newLine[index++] = elements[i];
    }
  }
  return newLine.join(' ');
}

// Strip CN from sdp before CN constraints is ready.
function removeCN(sdpLines, mLineIndex) {
  var mLineElements = sdpLines[mLineIndex].split(' ');
  // Scan from end for the convenience of removing an item.
  for (var i = sdpLines.length - 1; i >= 0; i--) {
    var payload = extractSdp(sdpLines[i], /a=rtpmap:(\d+) CN\/\d+/i);
    if (payload) {
      var cnPos = mLineElements.indexOf(payload);
      if (cnPos !== -1) {
        // Remove CN payload from m line.
        mLineElements.splice(cnPos, 1);
      }
      // Remove CN line in sdp
      sdpLines.splice(i, 1);
    }
  }

  sdpLines[mLineIndex] = mLineElements.join(' ');
  return sdpLines;
}

// Define helper functions.

// Gets the "other" peer connection.
function getOtherPeer(peerConnection) {
  return (peerConnection === pc) ?
      null : pc;
}

// Gets the name of a certain peer connection.
function getPeerName(peerConnection) {
  return (peerConnection === pc) ?
      'localPeerConnection' : 'remotePeerConnection';
}

// Logs an action (text) and the time when it happened on the console.
function trace(text) {
  text = text.trim();
  const now = (window.performance.now() / 1000).toFixed(3);

  console.log(now, text);
}
