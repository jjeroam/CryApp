import io
import logging
import socketserver
from threading import Condition, Thread, Lock
from http import server
from picamera2 import Picamera2
import cv2
import pyaudio
import json
import time
import wave
import tkinter as tk
from tkinter import scrolledtext
import subprocess

# Audio settings
CHUNK = 1024
FORMAT = pyaudio.paInt16
CHANNELS = 1
RATE = 44100

p = pyaudio.PyAudio()
audio_stream = p.open(format=FORMAT, channels=CHANNELS, rate=RATE, input=True, frames_per_buffer=CHUNK)

# Shared streaming output
class StreamingOutput:
    def __init__(self):
        self.frame = None
        self.condition = Condition()

    def set_frame(self, frame):
        with self.condition:
            self.frame = frame
            self.condition.notify_all()

output = StreamingOutput()

# Global flags and locks
sound_detected_flag = False
sound_lock = Lock()

# HTTP server handler
class StreamingHandler(server.BaseHTTPRequestHandler):
    def do_GET(self):
        global sound_detected_flag

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

        elif self.path == '/record-audio':
            try:
                filename = 'cry.wav'
                frames = []
                start_time = time.time()
                while time.time() - start_time < 5:
                    data = audio_stream.read(CHUNK)
                    frames.append(data)
                with wave.open(filename, 'wb') as wf:
                    wf.setnchannels(CHANNELS)
                    wf.setsampwidth(p.get_sample_size(FORMAT))
                    wf.setframerate(RATE)
                    wf.writeframes(b''.join(frames))

                with open(filename, 'rb') as f:
                    audio_data = f.read()

                self.send_response(200)
                self.send_header('Content-Type', 'audio/wav')
                self.send_header('Content-Disposition', f'attachment; filename="{filename}"')
                self.send_header('Content-Length', str(len(audio_data)))
                self.end_headers()
                self.wfile.write(audio_data)

            except Exception as e:
                logging.error(f"Error during audio recording: {e}")
                self.send_error(500, "Failed to record or send audio.")

        elif self.path == '/sound-detected':
            with sound_lock:
                sound_detected_flag = True
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({'status': 'sound detected'}).encode())

        elif self.path == '/poll-sound':
            with sound_lock:
                result = {'sound_detected': sound_detected_flag}
                sound_detected_flag = False
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps(result).encode())

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

# GUI
class App:
    def __init__(self, root):
        self.root = root
        self.root.title("Pi Server & DarkIce Controller")
        self.server = None
        self.darkice_process = None

        self.start_button = tk.Button(root, text="Start Server", command=self.toggle_server, width=30, bg="green", fg="white")
        self.start_button.pack(pady=10)

        self.log = scrolledtext.ScrolledText(root, width=60, height=15, state='disabled', bg="black", fg="lime")
        self.log.pack(padx=10, pady=10)

        self.camera_thread = None
        self.server_thread = None
        self.picam2 = None
        self.is_running = False

    def log_message(self, msg):
        self.log.config(state='normal')
        self.log.insert(tk.END, f"{time.strftime('%H:%M:%S')} - {msg}\n")
        self.log.see(tk.END)
        self.log.config(state='disabled')

    def toggle_server(self):
        if self.is_running:
            self.stop_server()
        else:
            self.start_server()

    def start_server(self):
        self.log_message("Starting camera...")
        self.picam2 = Picamera2()
        self.picam2.configure(self.picam2.create_video_configuration(main={"size": (640, 480)}))
        self.picam2.start()

        self.log_message("Starting camera thread...")
        self.camera_thread = Thread(target=start_camera_stream, args=(self.picam2, output))
        self.camera_thread.daemon = True
        self.camera_thread.start()

        self.log_message("Launching HTTP server...")
        address = ('', 8080)
        self.server = StreamingServer(address, StreamingHandler)
        self.server_thread = Thread(target=self.server.serve_forever)
        self.server_thread.daemon = True
        self.server_thread.start()

        self.log_message("Starting DarkIce...")
        try:
            self.darkice_process = subprocess.Popen(['sudo', 'darkice'])
        except Exception as e:
            self.log_message(f"Failed to start DarkIce: {e}")

        self.start_button.config(text="Stop Server", bg="red")
        self.is_running = True
        self.log_message("Server and DarkIce started.")

    def stop_server(self):
        self.log_message("Stopping server...")
        if self.server:
            self.server.shutdown()
            self.server.server_close()
            self.server = None

        self.log_message("Stopping DarkIce...")
        if self.darkice_process:
            self.darkice_process.terminate()
            self.darkice_process.wait()
            self.darkice_process = None

        if self.picam2:
            self.picam2.stop()

        self.is_running = False
        self.start_button.config(text="Start Server", bg="green")
        self.log_message("Server and DarkIce stopped.")

# Run the GUI
if __name__ == '__main__':
    root = tk.Tk()
    app = App(root)
    root.mainloop()

    audio_stream.stop_stream()
    audio_stream.close()
    p.terminate()



