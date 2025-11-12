"""
Glass AR Enhancements Package - P0 & P1 Features
Companion App Enhancements for Battery, Network, and Additional Features

This file extends the companion app with critical and high-priority features.
"""

from PyQt5.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QSlider, QCheckBox, QSpinBox, QComboBox, QGroupBox,
    QLineEdit, QTextEdit, QListWidget, QProgressBar
)
from PyQt5.QtCore import Qt, QTimer, pyqtSignal
from PyQt5.QtGui import QFont, QColor
import json
import time
from pathlib import Path
from datetime import datetime

# ==================== Battery & Network Monitoring ====================

class SystemMonitorWidget(QWidget):
    """
    Widget for displaying Glass battery and network status
    """

    def __init__(self):
        super().__init__()
        self.battery_level = 100
        self.is_charging = False
        self.network_latency = 0
        self.network_strength = 100

        self.setup_ui()

    def setup_ui(self):
        layout = QVBoxLayout(self)

        # Battery Group
        battery_group = QGroupBox("Glass Battery")
        battery_layout = QHBoxLayout()

        self.battery_icon = QLabel("🔋")
        self.battery_icon.setFont(QFont("Arial", 24))

        self.battery_label = QLabel("100%")
        self.battery_label.setFont(QFont("Arial", 16, QFont.Bold))

        self.battery_progress = QProgressBar()
        self.battery_progress.setRange(0, 100)
        self.battery_progress.setValue(100)
        self.battery_progress.setTextVisible(False)

        self.charging_label = QLabel("")
        self.charging_label.setStyleSheet("color: #00FF00;")

        battery_layout.addWidget(self.battery_icon)
        battery_layout.addWidget(self.battery_label)
        battery_layout.addWidget(self.battery_progress, 1)
        battery_layout.addWidget(self.charging_label)

        battery_group.setLayout(battery_layout)
        layout.addWidget(battery_group)

        # Network Group
        network_group = QGroupBox("Network Quality")
        network_layout = QVBoxLayout()

        signal_layout = QHBoxLayout()
        self.network_icon = QLabel("📶")
        self.network_icon.setFont(QFont("Arial", 24))

        self.network_label = QLabel("Excellent")
        self.network_label.setFont(QFont("Arial", 12))

        signal_layout.addWidget(self.network_icon)
        signal_layout.addWidget(self.network_label, 1)

        self.latency_label = QLabel("Latency: 0 ms")
        self.signal_label = QLabel("Signal: 100%")

        network_layout.addLayout(signal_layout)
        network_layout.addWidget(self.latency_label)
        network_layout.addWidget(self.signal_label)

        network_group.setLayout(network_layout)
        layout.addWidget(network_group)

    def update_battery(self, level, is_charging=False):
        """Update battery display"""
        self.battery_level = level
        self.is_charging = is_charging

        # Update icon
        if level > 80:
            self.battery_icon.setText("🔋")
            self.battery_label.setStyleSheet("color: #00FF00;")
        elif level > 50:
            self.battery_icon.setText("🔋")
            self.battery_label.setStyleSheet("color: #FFFF00;")
        elif level > 20:
            self.battery_icon.setText("🔋")
            self.battery_label.setStyleSheet("color: #FFA500;")
        else:
            self.battery_icon.setText("🪫")
            self.battery_label.setStyleSheet("color: #FF0000;")

        # Update label and progress bar
        self.battery_label.setText(f"{level}%")
        self.battery_progress.setValue(level)

        # Update charging status
        if is_charging:
            self.charging_label.setText("⚡ Charging")
            self.charging_label.setStyleSheet("color: #00FF00;")
        else:
            self.charging_label.setText("")

    def update_network(self, latency_ms, signal_strength):
        """Update network quality display"""
        self.network_latency = latency_ms
        self.network_strength = signal_strength

        # Determine quality
        if latency_ms < 50 and signal_strength > 80:
            quality = "Excellent"
            self.network_icon.setText("📶")
            self.network_label.setStyleSheet("color: #00FF00;")
        elif latency_ms < 100 and signal_strength > 60:
            quality = "Good"
            self.network_icon.setText("📶")
            self.network_label.setStyleSheet("color: #FFFF00;")
        elif latency_ms < 200 and signal_strength > 40:
            quality = "Fair"
            self.network_icon.setText("📡")
            self.network_label.setStyleSheet("color: #FFA500;")
        else:
            quality = "Poor"
            self.network_icon.setText("📡")
            self.network_label.setStyleSheet("color: #FF0000;")

        self.network_label.setText(quality)
        self.latency_label.setText(f"Latency: {latency_ms} ms")
        self.signal_label.setText(f"Signal: {signal_strength}%")

# ==================== Settings Persistence ====================

class SettingsManager:
    """
    Manages persistent settings for companion app
    """

    def __init__(self, config_file="companion_settings.json"):
        self.config_file = Path(config_file)
        self.settings = self.load_settings()

    def load_settings(self):
        """Load settings from file"""
        if self.config_file.exists():
            try:
                with open(self.config_file, 'r') as f:
                    return json.load(f)
            except Exception as e:
                print(f"Error loading settings: {e}")
                return self.default_settings()
        return self.default_settings()

    def save_settings(self):
        """Save settings to file"""
        try:
            with open(self.config_file, 'w') as f:
                json.dump(self.settings, f, indent=2)
        except Exception as e:
            print(f"Error saving settings: {e}")

    def default_settings(self):
        """Return default settings"""
        return {
            'server': {
                'host': 'localhost',
                'port': 8080,
                'auto_connect': True,
                'auto_start_server': False
            },
            'display': {
                'colormap': 'iron',
                'temperature_unit': 'celsius',
                'show_overlay': True,
                'show_fps': True,
                'fullscreen': False
            },
            'capture': {
                'auto_snapshot_enabled': False,
                'auto_snapshot_threshold': 80.0,
                'snapshot_format': 'jpg',
                'recording_quality': 'high'
            },
            'window': {
                'geometry': None,
                'position': None
            },
            'recent_connections': [
                'localhost',
                '127.0.0.1'
            ]
        }

    def get(self, category, key, default=None):
        """Get setting value"""
        return self.settings.get(category, {}).get(key, default)

    def set(self, category, key, value):
        """Set setting value"""
        if category not in self.settings:
            self.settings[category] = {}
        self.settings[category][key] = value
        self.save_settings()

    def get_recent_connections(self):
        """Get list of recent connection hosts"""
        return self.settings.get('recent_connections', ['localhost'])

    def add_recent_connection(self, host):
        """Add host to recent connections"""
        recent = self.settings.get('recent_connections', [])
        if host not in recent:
            recent.insert(0, host)
            self.settings['recent_connections'] = recent[:10]  # Keep last 10
            self.save_settings()

# ==================== Session Notes ====================

class SessionNotesWidget(QWidget):
    """
    Widget for taking timestamped notes during inspection
    """

    note_added = pyqtSignal(str, str)  # timestamp, note

    def __init__(self):
        super().__init__()
        self.notes = []
        self.setup_ui()

    def setup_ui(self):
        layout = QVBoxLayout(self)

        # Input area
        input_layout = QHBoxLayout()

        self.note_input = QLineEdit()
        self.note_input.setPlaceholderText("Enter note...")
        self.note_input.returnPressed.connect(self.add_note)

        add_btn = QPushButton("Add Note")
        add_btn.clicked.connect(self.add_note)

        input_layout.addWidget(self.note_input, 1)
        input_layout.addWidget(add_btn)

        layout.addLayout(input_layout)

        # Notes list
        self.notes_list = QTextEdit()
        self.notes_list.setReadOnly(True)
        self.notes_list.setFont(QFont("Consolas", 9))
        layout.addWidget(self.notes_list)

        # Controls
        controls_layout = QHBoxLayout()

        clear_btn = QPushButton("Clear All")
        clear_btn.clicked.connect(self.clear_notes)

        export_btn = QPushButton("Export Notes")
        export_btn.clicked.connect(self.export_notes)

        controls_layout.addWidget(clear_btn)
        controls_layout.addWidget(export_btn)
        controls_layout.addStretch()

        layout.addLayout(controls_layout)

    def add_note(self):
        """Add timestamped note"""
        text = self.note_input.text().strip()
        if not text:
            return

        timestamp = datetime.now().strftime("%H:%M:%S")
        note_entry = f"[{timestamp}] {text}"

        self.notes.append({
            'timestamp': timestamp,
            'text': text,
            'time': time.time()
        })

        self.notes_list.append(note_entry)
        self.note_input.clear()

        self.note_added.emit(timestamp, text)

    def clear_notes(self):
        """Clear all notes"""
        self.notes.clear()
        self.notes_list.clear()

    def export_notes(self):
        """Export notes to file"""
        if not self.notes:
            return

        filename = f"session_notes_{datetime.now().strftime('%Y%m%d_%H%M%S')}.txt"
        filepath = Path("./notes") / filename
        filepath.parent.mkdir(exist_ok=True)

        with open(filepath, 'w') as f:
            f.write(f"Session Notes - {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
            f.write("=" * 60 + "\n\n")
            for note in self.notes:
                f.write(f"[{note['timestamp']}] {note['text']}\n")

        print(f"Notes exported to {filepath}")

    def get_notes(self):
        """Get all notes as list"""
        return self.notes.copy()

# ==================== Temperature Tools ====================

class TemperatureMeasurementWidget(QWidget):
    """
    Widget for displaying temperature measurements
    """

    def __init__(self):
        super().__init__()
        self.temp_unit = 'celsius'
        self.measurements = {}
        self.setup_ui()

    def setup_ui(self):
        layout = QVBoxLayout(self)

        # Unit toggle
        unit_layout = QHBoxLayout()
        unit_layout.addWidget(QLabel("Unit:"))

        self.celsius_radio = QPushButton("°C")
        self.celsius_radio.setCheckable(True)
        self.celsius_radio.setChecked(True)
        self.celsius_radio.clicked.connect(lambda: self.set_unit('celsius'))

        self.fahrenheit_radio = QPushButton("°F")
        self.fahrenheit_radio.setCheckable(True)
        self.fahrenheit_radio.clicked.connect(lambda: self.set_unit('fahrenheit'))

        unit_layout.addWidget(self.celsius_radio)
        unit_layout.addWidget(self.fahrenheit_radio)
        unit_layout.addStretch()

        layout.addLayout(unit_layout)

        # Temperature displays
        self.center_temp_label = QLabel("Center: -- °C")
        self.center_temp_label.setFont(QFont("Arial", 14, QFont.Bold))
        layout.addWidget(self.center_temp_label)

        self.min_temp_label = QLabel("Min: -- °C")
        self.max_temp_label = QLabel("Max: -- °C")
        self.avg_temp_label = QLabel("Avg: -- °C")

        layout.addWidget(self.min_temp_label)
        layout.addWidget(self.max_temp_label)
        layout.addWidget(self.avg_temp_label)

        # Measurement points
        self.points_list = QListWidget()
        layout.addWidget(QLabel("Measurement Points:"))
        layout.addWidget(self.points_list)

        # Controls
        controls_layout = QHBoxLayout()

        add_point_btn = QPushButton("Add Point")
        add_point_btn.clicked.connect(self.add_measurement_point)

        clear_points_btn = QPushButton("Clear Points")
        clear_points_btn.clicked.connect(self.clear_points)

        controls_layout.addWidget(add_point_btn)
        controls_layout.addWidget(clear_points_btn)

        layout.addLayout(controls_layout)

    def set_unit(self, unit):
        """Set temperature unit"""
        self.temp_unit = unit
        self.celsius_radio.setChecked(unit == 'celsius')
        self.fahrenheit_radio.setChecked(unit == 'fahrenheit')
        self.update_display()

    def update_temperatures(self, center, min_temp, max_temp, avg):
        """Update temperature readings"""
        self.measurements = {
            'center': center,
            'min': min_temp,
            'max': max_temp,
            'avg': avg
        }
        self.update_display()

    def update_display(self):
        """Update temperature display with current unit"""
        if not self.measurements:
            return

        unit_symbol = "°C" if self.temp_unit == 'celsius' else "°F"

        def convert(temp_c):
            if self.temp_unit == 'fahrenheit':
                return temp_c * 9/5 + 32
            return temp_c

        self.center_temp_label.setText(
            f"Center: {convert(self.measurements['center']):.1f} {unit_symbol}"
        )
        self.min_temp_label.setText(
            f"Min: {convert(self.measurements['min']):.1f} {unit_symbol}"
        )
        self.max_temp_label.setText(
            f"Max: {convert(self.measurements['max']):.1f} {unit_symbol}"
        )
        self.avg_temp_label.setText(
            f"Avg: {convert(self.measurements['avg']):.1f} {unit_symbol}"
        )

    def add_measurement_point(self):
        """Add a measurement point"""
        if not self.measurements:
            return

        point_name = f"Point {self.points_list.count() + 1}"
        temp = self.measurements['center']
        unit = "°C" if self.temp_unit == 'celsius' else "°F"

        if self.temp_unit == 'fahrenheit':
            temp = temp * 9/5 + 32

        item_text = f"{point_name}: {temp:.1f} {unit}"
        self.points_list.addItem(item_text)

    def clear_points(self):
        """Clear all measurement points"""
        self.points_list.clear()

# ==================== Auto-Snapshot Configuration ====================

class AutoSnapshotWidget(QWidget):
    """
    Widget for configuring automatic snapshot capture
    """

    settings_changed = pyqtSignal(dict)

    def __init__(self):
        super().__init__()
        self.setup_ui()

    def setup_ui(self):
        layout = QVBoxLayout(self)

        # Enable/disable
        self.enable_check = QCheckBox("Enable Auto-Snapshot")
        self.enable_check.stateChanged.connect(self.on_settings_changed)
        layout.addWidget(self.enable_check)

        # Temperature threshold
        temp_layout = QHBoxLayout()
        temp_layout.addWidget(QLabel("Temperature Threshold:"))

        self.temp_threshold = QSpinBox()
        self.temp_threshold.setRange(30, 200)
        self.temp_threshold.setValue(80)
        self.temp_threshold.setSuffix(" °C")
        self.temp_threshold.valueChanged.connect(self.on_settings_changed)

        temp_layout.addWidget(self.temp_threshold)
        temp_layout.addStretch()

        layout.addLayout(temp_layout)

        # Confidence threshold
        conf_layout = QHBoxLayout()
        conf_layout.addWidget(QLabel("Confidence Threshold:"))

        self.conf_threshold = QSlider(Qt.Horizontal)
        self.conf_threshold.setRange(50, 100)
        self.conf_threshold.setValue(70)
        self.conf_threshold.valueChanged.connect(self.on_confidence_changed)

        self.conf_label = QLabel("70%")

        conf_layout.addWidget(self.conf_threshold)
        conf_layout.addWidget(self.conf_label)

        layout.addLayout(conf_layout)

        # Cooldown period
        cooldown_layout = QHBoxLayout()
        cooldown_layout.addWidget(QLabel("Cooldown Period:"))

        self.cooldown = QSpinBox()
        self.cooldown.setRange(1, 60)
        self.cooldown.setValue(5)
        self.cooldown.setSuffix(" seconds")
        self.cooldown.valueChanged.connect(self.on_settings_changed)

        cooldown_layout.addWidget(self.cooldown)
        cooldown_layout.addStretch()

        layout.addLayout(cooldown_layout)

        layout.addStretch()

    def on_confidence_changed(self, value):
        """Update confidence label"""
        self.conf_label.setText(f"{value}%")
        self.on_settings_changed()

    def on_settings_changed(self):
        """Emit settings when changed"""
        settings = self.get_settings()
        self.settings_changed.emit(settings)

    def get_settings(self):
        """Get current auto-snapshot settings"""
        return {
            'enabled': self.enable_check.isChecked(),
            'temp_threshold': self.temp_threshold.value(),
            'confidence_threshold': self.conf_threshold.value() / 100.0,
            'cooldown_seconds': self.cooldown.value()
        }

    def set_settings(self, settings):
        """Set auto-snapshot settings"""
        self.enable_check.setChecked(settings.get('enabled', False))
        self.temp_threshold.setValue(settings.get('temp_threshold', 80))
        self.conf_threshold.setValue(int(settings.get('confidence_threshold', 0.7) * 100))
        self.cooldown.setValue(settings.get('cooldown_seconds', 5))

# ==================== Thermal Colorbar Display ====================

class ThermalColorbarWidget(QWidget):
    """
    Widget for displaying thermal colormap legend
    """

    colormap_changed = pyqtSignal(str)

    def __init__(self):
        super().__init__()
        self.current_colormap = 'iron'
        self.min_temp = 0
        self.max_temp = 100
        self.setup_ui()

    def setup_ui(self):
        layout = QVBoxLayout(self)

        # Colormap selector
        selector_layout = QHBoxLayout()
        selector_layout.addWidget(QLabel("Colormap:"))

        self.colormap_combo = QComboBox()
        self.colormap_combo.addItems([
            'Iron',
            'Rainbow',
            'White Hot',
            'Black Hot',
            'Arctic',
            'Lava',
            'Grayscale'
        ])
        self.colormap_combo.currentTextChanged.connect(self.on_colormap_changed)
        selector_layout.addWidget(self.colormap_combo)
        selector_layout.addStretch()

        layout.addLayout(selector_layout)

        # Colorbar display
        self.colorbar_label = QLabel()
        self.colorbar_label.setMinimumHeight(40)
        self.colorbar_label.setAlignment(Qt.AlignCenter)
        layout.addWidget(self.colorbar_label)

        # Temperature range labels
        range_layout = QHBoxLayout()
        self.min_temp_label = QLabel("0°C")
        self.min_temp_label.setAlignment(Qt.AlignLeft)
        self.max_temp_label = QLabel("100°C")
        self.max_temp_label.setAlignment(Qt.AlignRight)

        range_layout.addWidget(self.min_temp_label)
        range_layout.addStretch()
        range_layout.addWidget(self.max_temp_label)

        layout.addLayout(range_layout)

        # Generate initial colorbar
        self.update_colorbar()

    def on_colormap_changed(self, colormap_name):
        """Handle colormap change"""
        self.current_colormap = colormap_name.lower().replace(' ', '_')
        self.update_colorbar()
        self.colormap_changed.emit(self.current_colormap)

    def update_colorbar(self):
        """Generate and display colorbar"""
        import cv2
        import numpy as np

        # Create gradient array (1 x 256)
        gradient = np.linspace(0, 255, 256, dtype=np.uint8).reshape(1, -1)

        # Apply colormap
        colormap_cv = self.get_cv_colormap(self.current_colormap)
        colored_gradient = cv2.applyColorMap(gradient, colormap_cv)

        # Resize to widget height
        height = 40
        colored_gradient = cv2.resize(colored_gradient, (256, height), interpolation=cv2.INTER_LINEAR)

        # Convert to QPixmap
        rgb_image = cv2.cvtColor(colored_gradient, cv2.COLOR_BGR2RGB)
        height, width, channel = rgb_image.shape
        bytes_per_line = 3 * width
        q_image = QImage(rgb_image.data, width, height, bytes_per_line, QImage.Format_RGB888)
        pixmap = QPixmap.fromImage(q_image)

        # Scale to fit widget
        scaled_pixmap = pixmap.scaled(
            self.colorbar_label.width(),
            self.colorbar_label.height(),
            Qt.IgnoreAspectRatio,
            Qt.SmoothTransformation
        )

        self.colorbar_label.setPixmap(scaled_pixmap)

    def get_cv_colormap(self, name):
        """Get OpenCV colormap constant"""
        colormaps = {
            'iron': cv2.COLORMAP_HOT,
            'rainbow': cv2.COLORMAP_RAINBOW,
            'white_hot': cv2.COLORMAP_BONE,
            'black_hot': cv2.COLORMAP_BONE,  # Inverted
            'arctic': cv2.COLORMAP_WINTER,
            'lava': cv2.COLORMAP_HOT,
            'grayscale': cv2.COLORMAP_BONE
        }
        return colormaps.get(name, cv2.COLORMAP_HOT)

    def set_temperature_range(self, min_temp, max_temp, unit='celsius'):
        """Update temperature range labels"""
        self.min_temp = min_temp
        self.max_temp = max_temp

        unit_symbol = "°C" if unit == 'celsius' else "°F"
        self.min_temp_label.setText(f"{min_temp:.0f}{unit_symbol}")
        self.max_temp_label.setText(f"{max_temp:.0f}{unit_symbol}")

    def set_colormap(self, colormap_name):
        """Set colormap programmatically"""
        index = self.colormap_combo.findText(
            colormap_name.replace('_', ' ').title(),
            Qt.MatchFixedString
        )
        if index >= 0:
            self.colormap_combo.setCurrentIndex(index)

# ==================== UI Mode Manager ====================

class UIModeManager(QWidget):
    """
    Widget for managing UI complexity levels (Simple/Advanced)
    """

    mode_changed = pyqtSignal(str)  # 'simple' or 'advanced'

    def __init__(self):
        super().__init__()
        self.current_mode = 'simple'
        self.setup_ui()

    def setup_ui(self):
        layout = QHBoxLayout(self)

        label = QLabel("UI Mode:")
        label.setFont(QFont("Arial", 10, QFont.Bold))
        layout.addWidget(label)

        self.simple_btn = QPushButton("🔵 Simple")
        self.simple_btn.setCheckable(True)
        self.simple_btn.setChecked(True)
        self.simple_btn.clicked.connect(lambda: self.set_mode('simple'))
        self.simple_btn.setToolTip("Show only essential controls")

        self.advanced_btn = QPushButton("🔧 Advanced")
        self.advanced_btn.setCheckable(True)
        self.advanced_btn.clicked.connect(lambda: self.set_mode('advanced'))
        self.advanced_btn.setToolTip("Show all features and controls")

        layout.addWidget(self.simple_btn)
        layout.addWidget(self.advanced_btn)
        layout.addStretch()

    def set_mode(self, mode):
        """Set UI mode"""
        self.current_mode = mode
        self.simple_btn.setChecked(mode == 'simple')
        self.advanced_btn.setChecked(mode == 'advanced')

        # Update button styles
        if mode == 'simple':
            self.simple_btn.setStyleSheet("background-color: #4CAF50; color: white; font-weight: bold;")
            self.advanced_btn.setStyleSheet("")
        else:
            self.simple_btn.setStyleSheet("")
            self.advanced_btn.setStyleSheet("background-color: #FF9800; color: white; font-weight: bold;")

        self.mode_changed.emit(mode)

    def get_mode(self):
        """Get current UI mode"""
        return self.current_mode

# ==================== Integration Instructions ====================

"""
To integrate these enhancements into glass_companion_app.py:

1. Import this module:
   from glass_enhancements_p0_p1 import (
       SystemMonitorWidget, SettingsManager, SessionNotesWidget,
       TemperatureMeasurementWidget, AutoSnapshotWidget, ThermalColorbarWidget
   )

2. In GlassCompanionApp.__init__():
   self.settings_manager = SettingsManager()
   self.load_persistent_settings()

3. Add widgets to appropriate tabs:
   # In create_connection_tab():
   self.system_monitor = SystemMonitorWidget()
   layout.addWidget(self.system_monitor)

   # In create_control_tab():
   self.temp_widget = TemperatureMeasurementWidget()
   layout.addWidget(self.temp_widget)

   self.auto_snapshot = AutoSnapshotWidget()
   layout.addWidget(self.auto_snapshot)

   # In create_recording_tab():
   self.notes_widget = SessionNotesWidget()
   layout.addWidget(self.notes_widget)

4. Connect Socket.IO events:
   @self.socket_client.on('battery_status')
   def on_battery_status(data):
       level = data['battery_level']
       charging = data['is_charging']
       self.system_monitor.update_battery(level, charging)

   @self.socket_client.on('network_stats')
   def on_network_stats(data):
       latency = data['latency_ms']
       signal = data['signal_strength']
       self.system_monitor.update_network(latency, signal)

5. Save/load settings:
   def load_persistent_settings(self):
       # Server settings
       host = self.settings_manager.get('server', 'host', 'localhost')
       port = self.settings_manager.get('server', 'port', 8080)
       self.host_input.setCurrentText(host)
       self.port_input.setValue(port)

       # Auto-snapshot settings
       auto_snap = self.settings_manager.settings.get('capture', {})
       self.auto_snapshot.set_settings(auto_snap)

   def save_persistent_settings(self):
       self.settings_manager.set('server', 'host', self.host_input.currentText())
       self.settings_manager.set('server', 'port', self.port_input.value())

       auto_snap = self.auto_snapshot.get_settings()
       for key, value in auto_snap.items():
           self.settings_manager.set('capture', key, value)

6. Call save_persistent_settings() in closeEvent()
"""

print("Glass AR Enhancements P0/P1 module loaded successfully")
print("Ready to integrate into glass_companion_app.py")
