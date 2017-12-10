from autobahn.twisted.websocket import WebSocketServerProtocol, \
    WebSocketServerFactory
from twisted.internet import reactor
from twisted.python import log
from subprocess import Popen
import threading
import random
import time
import json
import sys
import pdb
import os


class SoundClient(object):
    """
    This class creates a thread to play an audio file every 'interval_seconds'.
    By executing 'adjustSound' you can alter the melody that is being played.
    """
    devnull = open(os.devnull, 'w')  # used to discard the output from Popen
    tones_folder = 'tones/'

    def __init__(self, instrument):
        self.instrument = instrument
        self.melody = 1
        self.interval_seconds = 1

    def adjustSound(self, data_str):
        json_data = json.loads(data_str)
        orientation_y = float(json_data['orientation_y'])

        if orientation_y > 0.3 and orientation_y < 0.6:
            self.melody = 1
        elif orientation_y > 0.0 and orientation_y < 0.3:
            self.melody = 2
        elif orientation_y > -0.3 and orientation_y < -0.0:
            self.melody = 3
        elif orientation_y > -0.6 and orientation_y < -0.3:
            self.melody = 4
        elif orientation_y > -0.9 and orientation_y < -0.6:
            self.melody = 5
        elif orientation_y > -1.2 and orientation_y < -0.9:
            self.melody = 6

    def playSound(self):
        Popen(['mpg123', '{}{}{}.mp3'.format(self.tones_folder, 
                                             self.instrument, 
                                             self.melody)], 
              stderr=SoundClient.devnull)

    def start(self):
        self.playSound()
        t = threading.Timer(self.interval_seconds, self.start)
        t.setDaemon(True)
        t.start()


class MyServerProtocol(WebSocketServerProtocol):
    def __init__(self):
        super(MyServerProtocol, self).__init__()
        self.clients = {}

    def onConnect(self, request):
        # if this is a new client, create a SoundClient instance to store it
        if self.clients.get(request.peer) is None:
            self.clients[request.peer] = SoundClient('guitar')
            self.clients[request.peer].start()
            
        print("Client connecting: {}".format(request.peer))

    def onOpen(self):
        print("WebSocket connection open.")

    def onMessage(self, payload, isBinary):
        if not isBinary:
            print("{}".format(payload.decode('utf8')))
            #print(self.clients)
            #print(self.peer)

            # take the current device's sensor data to update the sound wave
            self.clients[self.peer].adjustSound(payload.decode('utf8'))

    def onClose(self, wasClean, code, reason):
        print("WebSocket connection closed: {}".format(reason))


if __name__ == '__main__':
    log.startLogging(sys.stdout)

    factory = WebSocketServerFactory(u"ws://127.0.0.1:9000")
    factory.protocol = MyServerProtocol
    reactor.listenTCP(9000, factory)
    reactor.run()
