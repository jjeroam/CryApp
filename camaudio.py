import io
import logging
import socketserver
from threading import Condition, Thread
from http import server
from picamera2 import Picamera2
import cv2
import pyaudio

PAGE = """
<html>
<body>
<center>
    <h2>Live Video Stream</h2>
    <img src="stream.mjpg" width="640" height="480"><br>
    <h2>Audio Stream</h2>
    <audio controls autoplay>
        <source src="/audio" type="audio/wav">
        Your browser does not support the audio element.
    </audio>
</center>
</body>
</html>
"""

# Audio settings
CHUNK = 1024
FORMAT = pyaudio.paInt16
CHANNELS = 1
RATE = 44100

p = pyaudio.PyAudio()
audio_stream = p.open(format=FORMAT,
                      channels=CHANNELS,
                      rate=RATE,
                      input=True,
                      frames_per_buffer=CHUNK)

class StreamingOutput:
    def __init__(self):
        self.frame = None
        self.condition = Condition()

    def set_frame(self, frame):
        with self.condition:
            self.frame = frame
            self.condition.notify_all()

class StreamingHandler(server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/':
            self.send_response(301)
            self.send_header('Location', '/index.html')
            self.end_headers()
        elif self.path == '/index.html':
            content = PAGE.encode('utf-8')
            self.send_response(200)
            self.send_header('Content-Type', 'text/html')
            self.send_header('Content-Length', len(content))
            self.end_headers()
            self.wfile.write(content)
        elif self.path == '/stream.mjpg':
            self.send_response(200)
            self.send_header('Age', '0')
            self.send_header('Cache-Control', 'no-cache, private')
            self.send_header('Pragma', 'no-cache')
            self.send_header('Content-Type', 'multipart/x-mixed-replace; boundary=FRAME')
            self.end_headers()
            try:
                while True:
                    with output.condition:
                        output.condition.wait()
                        frame = output.frame
                    self.wfile.write(b'--FRAME\r\n')
                    self.send_header('Content-Type', 'image/jpeg')
                    self.send_header('Content-Length', str(len(frame)))
                    self.end_headers()
                    self.wfile.write(frame)
                    self.wfile.write(b'\r\n')
            except Exception as e:
                logging.warning('Removed streaming client %s: %s', self.client_address, str(e))
        elif self.path == '/audio':
            self.send_response(200)
            self.send_header('Content-Type', 'audio/wav')
            self.send_header('Cache-Control', 'no-cache')
            self.end_headers()
            try:
                while True:
                    data = audio_stream.read(CHUNK)
                    self.wfile.write(data)
            except Exception as e:
                logging.warning('Audio stream client disconnected %s: %s', self.client_address, str(e))
        else:
            self.send_error(404)
            self.end_headers()

class StreamingServer(socketserver.ThreadingMixIn, server.HTTPServer):
    allow_reuse_address = True
    daemon_threads = True

def start_camera_stream(picam2, output):
    while True:
        frame = picam2.capture_array()
        ret, jpeg = cv2.imencode('.jpg', frame)
        if ret:
            output.set_frame(jpeg.tobytes())

# Initialize camera
picam2 = Picamera2()
picam2.configure(picam2.create_video_configuration(main={"size": (640, 480)}))
picam2.start()

output = StreamingOutput()

# Start background capture thread
camera_thread = Thread(target=start_camera_stream, args=(picam2, output))
camera_thread.daemon = True
camera_thread.start()

try:
    address = ('192.168.254.151', 8081)  # Bind to all interfaces
    server = StreamingServer(address, StreamingHandler)
    print("Server started at http://192.18.254.151:8081")
    server.serve_forever()
finally:
    picam2.stop()
    audio_stream.stop_stream()
    audio_stream.close()
    p.terminate()



