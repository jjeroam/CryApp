import io
import logging
import socketserver
import subprocess
from threading import Condition, Thread, Lock
from http import server
from picamera2 import Picamera2
import cv2
import pyaudio
import json
import time
import wave
import os
import tkinter as tk
from tkinter import scrolledtext

# HTML page
PAGE = """
<html>
<body>
<center>
    <h2>Live Video Stream</h2>
    <img src="stream.mjpg" width="640" height="480"><br>
    <h2>Audio Stream</h2>
    <audio controls autoplay>
        <source src="http://192.168.254.151:8000/mystream" type="audio/mpeg">
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
RECORD_SECONDS = 5
OUTPUT_FILENAME = "cry.wav"
THRESHOLD = 500

p = pyaudio.PyAudio()

class StreamingOutput:
    def __init__(self):
        self.frame = None
        self.condition = Condition()

    def set_frame(self, frame):
        with self.condition:
            self.frame = frame
            self.condition.notify_all()

output = StreamingOutput()
sound_detected_flag = False
sound_rms_level = 0
sound_lock = Lock()

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

        elif self.path == '/poll-sound':
            with sound_lock:
                result = {'sound_detected': sound_detected_flag, 'rms': sound_rms_level}
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps(result).encode())

        elif self.path == '/record-audio':
            self.record_audio()
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({"status": "recorded"}).encode())

        elif self.path == '/cry.wav':
            if os.path.exists(OUTPUT_FILENAME):
                with open(OUTPUT_FILENAME, 'rb') as f:
                    data = f.read()
                self.send_response(200)
                self.send_header('Content-Type', 'audio/wav')
                self.send_header('Content-Length', str(len(data)))
                self.end_headers()
                self.wfile.write(data)
            else:
                self.send_error(404, "cry.wav not found")
        else:
            self.send_error(404)
            self.end_headers()

    def record_audio(self):
        frames = []
        stream = p.open(format=FORMAT,
                        channels=CHANNELS,
                        rate=RATE,
                        input=True,
                        frames_per_buffer=CHUNK)
        for _ in range(0, int(RATE / CHUNK * RECORD_SECONDS)):
            data = stream.read(CHUNK)
            frames.append(data)
        stream.stop_stream()
        stream.close()

        wf = wave.open(OUTPUT_FILENAME, 'wb')
        wf.setnchannels(CHANNELS)
        wf.setsampwidth(p.get_sample_size(FORMAT))
        wf.setframerate(RATE)
        wf.writeframes(b''.join(frames))
        wf.close()

class StreamingServer(socketserver.ThreadingMixIn, server.HTTPServer):
    allow_reuse_address = True
    daemon_threads = True

def start_camera_stream(picam2, output):
    while True:
        frame = picam2.capture_array()
        ret, jpeg = cv2.imencode('.jpg', frame)
        if ret:
            output.set_frame(jpeg.tobytes())

def sound_detection_loop(update_gui_callback):
    global sound_detected_flag, sound_rms_level
    stream = p.open(format=FORMAT, channels=CHANNELS, rate=RATE, input=True, frames_per_buffer=CHUNK)
    while True:
        data = stream.read(CHUNK, exception_on_overflow=False)
        audio_data = memoryview(data).cast('h')
        rms = (sum(sample * sample for sample in audio_data) / len(audio_data)) ** 0.5
        with sound_lock:
            sound_detected_flag = rms > THRESHOLD
            sound_rms_level = int(rms)
        update_gui_callback(sound_detected_flag, sound_rms_level)
        time.sleep(0.5)

class App:
    def __init__(self, master):
        self.master = master
        master.title("Raspberry Pi Stream Server")
        master.geometry("600x350")

        self.start_button = tk.Button(master, text="Start Server", command=self.toggle_server, bg="green", fg="white", height=2, width=20)
        self.start_button.pack(pady=10)

        self.status_label = tk.Label(master, text="Sound Detection: Waiting...", font=("Arial", 14))
        self.status_label.pack(pady=5)

        self.rms_label = tk.Label(master, text="RMS Level: 0", font=("Arial", 12))
        self.rms_label.pack(pady=5)

        self.log = scrolledtext.ScrolledText(master, width=70, height=10, state='disabled')
        self.log.pack()

        self.is_running = False
        self.picam2 = None
        self.server = None
        self.darkice_process = None

    def toggle_server(self):
        if self.is_running:
            self.stop_server()
        else:
            self.start_server()

    def log_message(self, message):
        self.log.configure(state='normal')
        self.log.insert(tk.END, message + '\n')
        self.log.configure(state='disabled')
        self.log.see(tk.END)

    def update_gui_status(self, detected, rms):
        self.status_label.config(text=f"Sound Detection: {'Detected' if detected else 'No Sound'}", fg="green" if detected else "red")
        self.rms_label.config(text=f"RMS Level: {rms}")

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
            self.darkice_process = subprocess.Popen(
                ['darkice'],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE
            )
            Thread(target=self.log_darkice_output, daemon=True).start()
        except Exception as e:
            self.log_message(f"Failed to start DarkIce: {e}")

        self.log_message("Starting sound detection...")
        self.sound_thread = Thread(target=sound_detection_loop, args=(self.update_gui_status,))
        self.sound_thread.daemon = True
        self.sound_thread.start()

        self.start_button.config(text="Stop Server", bg="red")
        self.is_running = True
        self.log_message("Server, DarkIce, and sound detection started.")

    def stop_server(self):
        self.log_message("Stopping server...")

        if self.server:
            self.server.shutdown()
            self.server.server_close()
            self.server = None

        if self.picam2:
            self.picam2.stop()

        if self.darkice_process:
            self.darkice_process.terminate()
            self.darkice_process.wait()

        self.start_button.config(text="Start Server", bg="green")
        self.is_running = False
        self.log_message("Server and DarkIce stopped.")

    def log_darkice_output(self):
        if self.darkice_process:
            for line in iter(self.darkice_process.stderr.readline, b''):
                self.log_message(f"[DarkIce] {line.decode().strip()}")

if __name__ == "__main__":
    root = tk.Tk()
    app = App(root)
    root.mainloop()

    p.terminate()



