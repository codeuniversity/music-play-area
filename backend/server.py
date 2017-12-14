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
    instruments = ['Guitar', 'Piano', 'drum']

    def __init__(self):
        self.instrument = SoundClient.instruments[2]
        self.melody = 1
        self.interval_seconds = 0.3

    def adjustSound(self, data_str):
        json_data = json.loads(data_str)
        self.instrument = SoundClient.instruments[int(json_data['instrument_id'])]
        self.interval_seconds = 0.3 * int(json_data['freq'])
        print(self.interval_seconds)
        orientation_y = float(json_data['orientation_y'].replace(',', '.'))

        if 0.3 < orientation_y < 0.6:
            self.melody = 1
        elif 0.0 < orientation_y < 0.3:
            self.melody = 2
        elif -0.3 < orientation_y < -0.0:
            self.melody = 3
        elif -0.6 < orientation_y < -0.3:
            self.melody = 4
        elif -0.9 < orientation_y < -0.6:
            self.melody = 5
        elif -1.2 < orientation_y < -0.9:
            self.melody = 6

    def playSound(self):
        if self.instrument is None or self.melody is None or self.interval_seconds is None:
            # wait for the instrument and melody to be selected before playing
            print('error: ', self.instrument, self.melody, self.interval_seconds)
            return

        print(self.instrument, self.melody, self.interval_seconds)
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
            self.clients[request.peer] = SoundClient()
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
