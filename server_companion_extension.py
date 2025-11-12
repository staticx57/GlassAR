"""
Server Extension for Glass AR Companion App Support

Adds Socket.IO events and handlers for remote control and monitoring
via the companion app running on ThinkPad P16.
"""

def setup_companion_events(socketio, processor):
    """
    Setup Socket.IO events for companion app integration

    Args:
        socketio: Flask-SocketIO instance
        processor: Boson320Processor instance
    """

    # Track connected clients
    glass_clients = set()
    companion_clients = set()

    @socketio.on('connect')
    def handle_connect():
        """Handle client connection"""
        print(f'[Companion] Client connected: {request.sid}')

    @socketio.on('disconnect')
    def handle_disconnect():
        """Handle client disconnection"""
        sid = request.sid

        if sid in glass_clients:
            glass_clients.remove(sid)
            # Notify companion apps that Glass disconnected
            socketio.emit('glass_disconnected', {}, room=None, skip_sid=sid)
            print(f'[Companion] Glass disconnected: {sid}')

        if sid in companion_clients:
            companion_clients.remove(sid)
            print(f'[Companion] Companion app disconnected: {sid}')

    @socketio.on('register_glass')
    def handle_register_glass():
        """Register client as Glass device"""
        sid = request.sid
        glass_clients.add(sid)

        # Notify companion apps that Glass connected
        socketio.emit('glass_connected', {'glass_id': sid}, room=None, skip_sid=sid)
        print(f'[Companion] Glass registered: {sid}')

    @socketio.on('register_companion')
    def handle_register_companion():
        """Register client as companion app"""
        sid = request.sid
        companion_clients.add(sid)

        # Send current Glass connection status
        glass_connected = len(glass_clients) > 0
        socketio.emit('glass_connected' if glass_connected else 'glass_disconnected',
                     room=sid)

        print(f'[Companion] Companion app registered: {sid}')

    @socketio.on('thermal_frame')
    def handle_thermal_frame(data):
        """
        Receive thermal frame from Glass, process it, and broadcast to companions
        """
        sid = request.sid

        try:
            # Process frame with AI (existing processor logic)
            # This would call the existing Boson320Processor methods

            # Extract thermal data for temperature measurements
            thermal_measurements = {
                'center_temp': data.get('center_temp', 0),
                'min_temp': data.get('min_temp', 0),
                'max_temp': data.get('max_temp', 0),
                'avg_temp': data.get('avg_temp', 0),
                'timestamp': time.time()
            }

            # For now, just forward with annotations
            processed_data = {
                'frame': data.get('frame'),
                'mode': data.get('mode', 'thermal_only'),
                'detections': [],  # Would come from processor
                'thermal_anomalies': {},  # Would come from processor
                'timestamp': time.time(),
                'glass_id': sid
            }

            # Send back to Glass with annotations
            socketio.emit('annotations', processed_data, room=sid)

            # Broadcast to all companion apps
            socketio.emit('thermal_frame_processed', processed_data,
                         room=None, skip_sid=sid)

            # Broadcast thermal measurements separately
            socketio.emit('thermal_data', thermal_measurements,
                         room=None, skip_sid=sid)

        except Exception as e:
            print(f'[Companion] Error processing frame: {e}')
            socketio.emit('error', {'message': str(e)}, room=sid)

    @socketio.on('battery_status')
    def handle_battery_status(data):
        """
        Receive battery status from Glass and broadcast to companions
        """
        sid = request.sid

        # Broadcast battery status to all companion apps
        socketio.emit('battery_status', data, room=None, skip_sid=sid)
        print(f'[Companion] Battery status: {data.get("battery_level")}% '
              f'(charging: {data.get("is_charging", False)})')

    @socketio.on('network_stats')
    def handle_network_stats(data):
        """
        Receive network statistics from Glass and broadcast to companions
        """
        sid = request.sid

        # Broadcast network stats to all companion apps
        socketio.emit('network_stats', data, room=None, skip_sid=sid)
        print(f'[Companion] Network stats: {data.get("latency_ms")}ms latency, '
              f'{data.get("signal_strength")}% signal')

    # ===== Remote Control Commands =====

    @socketio.on('set_mode')
    def handle_set_mode(data):
        """Forward mode change command to Glass"""
        mode = data.get('mode')

        # Send to all Glass clients
        for glass_sid in glass_clients:
            socketio.emit('set_mode', {'mode': mode}, room=glass_sid)

        print(f'[Companion] Mode change requested: {mode}')

    @socketio.on('capture_snapshot')
    def handle_capture_snapshot():
        """Trigger snapshot capture on Glass"""
        for glass_sid in glass_clients:
            socketio.emit('capture_snapshot', {}, room=glass_sid)

        print('[Companion] Snapshot capture triggered')

    @socketio.on('start_recording')
    def handle_start_recording():
        """Start recording on Glass"""
        for glass_sid in glass_clients:
            socketio.emit('start_recording', {}, room=glass_sid)

        print('[Companion] Recording started')

    @socketio.on('stop_recording')
    def handle_stop_recording():
        """Stop recording on Glass"""
        for glass_sid in glass_clients:
            socketio.emit('stop_recording', {}, room=glass_sid)

        print('[Companion] Recording stopped')

    @socketio.on('previous_detection')
    def handle_previous_detection():
        """Navigate to previous detection"""
        for glass_sid in glass_clients:
            socketio.emit('previous_detection', {}, room=glass_sid)

    @socketio.on('next_detection')
    def handle_next_detection():
        """Navigate to next detection"""
        for glass_sid in glass_clients:
            socketio.emit('next_detection', {}, room=glass_sid)

    @socketio.on('toggle_overlay')
    def handle_toggle_overlay():
        """Toggle overlay on Glass"""
        for glass_sid in glass_clients:
            socketio.emit('toggle_overlay', {}, room=glass_sid)

    @socketio.on('set_auto_snapshot')
    def handle_set_auto_snapshot(data):
        """Configure auto-snapshot settings on Glass"""
        for glass_sid in glass_clients:
            socketio.emit('set_auto_snapshot', data, room=glass_sid)

        print(f'[Companion] Auto-snapshot settings updated: {data}')

    # ===== System Information =====

    @socketio.on('get_stats')
    def handle_get_stats():
        """Send system statistics to companion"""
        stats = {
            'glass_connected': len(glass_clients) > 0,
            'companion_clients': len(companion_clients),
            'processor_stats': processor.stats if processor else {},
            'gpu_available': torch.cuda.is_available() if 'torch' in globals() else False,
            'timestamp': time.time()
        }

        socketio.emit('stats', stats, room=request.sid)

    print('[Companion] Socket.IO events registered for companion app')


# To integrate into thermal_ar_server.py, add at the end:
"""
# Add this at the end of thermal_ar_server.py:

from server_companion_extension import setup_companion_events

# After creating socketio and processor:
setup_companion_events(socketio, processor)
"""
