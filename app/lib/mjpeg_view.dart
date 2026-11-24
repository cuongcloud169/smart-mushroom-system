import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

/// Hiển thị luồng video MJPEG (camera IP) bằng thư viện http có sẵn.
/// MJPEG = chuỗi ảnh JPEG nối tiếp: mỗi ảnh bắt đầu bằng FF D8 và kết thúc bằng FF D9.
/// Widget đọc luồng byte, cắt ra từng ảnh rồi hiển thị ảnh mới nhất; mất kết nối thì tự nối lại sau 3 giây.
class MjpegView extends StatefulWidget {
  final String url;
  final String errorText;
  const MjpegView({super.key, required this.url, this.errorText = 'Không kết nối được camera'});

  @override
  State<MjpegView> createState() => _MjpegViewState();
}

class _MjpegViewState extends State<MjpegView> {
  http.Client? _client;
  StreamSubscription<List<int>>? _sub;
  Timer? _retry;
  Uint8List? _frame;
  bool _failed = false;
  final List<int> _buf = [];
  static const int _maxBuffer = 4 * 1024 * 1024; // tránh tràn bộ nhớ nếu luồng hỏng

  @override
  void initState() {
    super.initState();
    _start();
  }

  @override
  void didUpdateWidget(MjpegView old) {
    super.didUpdateWidget(old);
    if (old.url != widget.url) {
      _stop();
      _start();
    }
  }

  Future<void> _start() async {
    _buf.clear();
    final client = http.Client();
    _client = client;
    try {
      final res = await client.send(http.Request('GET', Uri.parse(widget.url))).timeout(const Duration(seconds: 10));
      if (res.statusCode != 200) throw Exception('HTTP ${res.statusCode}');
      _sub = res.stream.listen(_onData, onError: (_) => _fail(), onDone: _fail, cancelOnError: true);
    } catch (_) {
      _fail();
    }
  }

  void _onData(List<int> chunk) {
    _buf.addAll(chunk);
    Uint8List? latest;
    while (true) {
      final start = _indexOf(0xD8, 0);
      if (start < 0) {
        if (_buf.length > 1) _buf.removeRange(0, _buf.length - 1); // giữ byte cuối (có thể là FF)
        break;
      }
      final end = _indexOf(0xD9, start + 2);
      if (end < 0) {
        if (start > 0) _buf.removeRange(0, start);
        break;
      }
      latest = Uint8List.fromList(_buf.sublist(start, end + 2));
      _buf.removeRange(0, end + 2);
    }
    if (_buf.length > _maxBuffer) _buf.clear();
    if (latest != null && mounted) {
      setState(() {
        _frame = latest;
        _failed = false;
      });
    }
  }

  /// Vị trí của cặp byte FF <marker> trong bộ đệm, -1 nếu chưa có
  int _indexOf(int marker, int from) {
    for (var i = from; i < _buf.length - 1; i++) {
      if (_buf[i] == 0xFF && _buf[i + 1] == marker) return i;
    }
    return -1;
  }

  void _fail() {
    _stop();
    if (!mounted) return;
    setState(() => _failed = true);
    _retry = Timer(const Duration(seconds: 3), () {
      if (mounted) _start();
    });
  }

  void _stop() {
    _retry?.cancel();
    _sub?.cancel();
    _sub = null;
    _client?.close();
    _client = null;
  }

  @override
  void dispose() {
    _stop();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_frame != null) {
      return Image.memory(_frame!, gaplessPlayback: true, fit: BoxFit.contain);
    }
    return SizedBox(
      height: 160,
      child: Center(child: _failed ? Text(widget.errorText) : const CircularProgressIndicator()),
    );
  }
}
