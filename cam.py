import io
import logging
import socketserver
from threading import Condition, Thread
from http import server
from picamera2 import Picamera2
import cv2
import wave
import pyaudio
import json

PAGE = """
<html>
<body>
<center><img src="stream.mjpg" width="640" height="480"></center>
</body>
</html>
"""

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
        elif self.path == '/recorded_audio':
            # Serve the recorded audio file
            self.send_response(200)
            self.send_header('Content-Type', 'audio/wav')
            self.end_headers()
            with open('recorded_audio.wav', 'rb') as f:
                self.wfile.write(f.read())
        else:
            self.send_error(404)
            self.end_headers()

    def do_POST(self):
        if self.path == '/start_recording':
            # Start recording audio on receiving a POST request
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)

            # Parse JSON to verify the action
            try:
                data = json.loads(post_data.decode('utf-8'))
                if data.get('action') == 'start_recording':
                    self.start_audio_recording()
                    self.send_response(200)
                    self.end_headers()
                    self.wfile.write(b"Recording started")
                    return
            except json.JSONDecodeError:
                self.send_response(400)
                self.end_headers()
                self.wfile.write(b"Invalid JSON")
        else:
            self.send_error(404)
            self.end_headers()

    def start_audio_recording(self):
        # Use PyAudio to record audio
        audio = pyaudio.PyAudio()
        stream = audio.open(format=pyaudio.paInt16,
                            channels=1,
                            rate=44100,
                            input=True,
                            frames_per_buffer=1024)
        print("Recording started...")
        
        frames = []
        for _ in range(0, int(44100 / 1024 * 5)):  # Record for 5 seconds
            data = stream.read(1024)
            frames.append(data)
        
        print("Recording finished...")
        stream.stop_stream()
        stream.close()
        audio.terminate()
        
        # Save the recorded audio to a WAV file
        with wave.open('recorded_audio.wav', 'wb') as wf:
            wf.setnchannels(1)
            wf.setsampwidth(audio.get_sample_size(pyaudio.paInt16))
            wf.setframerate(44100)
            wf.writeframes(b''.join(frames))
        print("Audio saved as recorded_audio.wav")

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
    address = ('192.168.254.151', 8081)  # Adjust if you want to bind to a specific IP
    server = StreamingServer(address, StreamingHandler)
    print("Server started at http://192.168.254.151:8081")
    server.serve_forever()
finally:
    picam2.stop()



