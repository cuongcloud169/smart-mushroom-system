import 'dart:math' as math;

import 'package:flutter/material.dart';

import 'api.dart';

void toast(BuildContext c, String m) => ScaffoldMessenger.of(c).showSnackBar(SnackBar(content: Text(m)));

String errText(Object e) => e.toString().replaceFirst('Exception: ', '');

/// Hộp thoại nhập 1 dòng chữ
Future<String?> askText(BuildContext context, String title, {String hint = ''}) {
  final ctl = TextEditingController();
  return showDialog<String>(
    context: context,
    builder: (dialogContext) => AlertDialog(
      title: Text(title),
      content: TextField(controller: ctl, decoration: InputDecoration(hintText: hint), autofocus: true),
      actions: [
        TextButton(onPressed: () => Navigator.pop(dialogContext), child: const Text('Hủy')),
        FilledButton(onPressed: () => Navigator.pop(dialogContext, ctl.text.trim()), child: const Text('OK')),
      ],
    ),
  );
}

Future<bool> confirmDialog(BuildContext context, String message) async {
  final ok = await showDialog<bool>(
    context: context,
    builder: (dialogContext) => AlertDialog(
      content: Text(message),
      actions: [
        TextButton(onPressed: () => Navigator.pop(dialogContext, false), child: const Text('Không')),
        FilledButton(onPressed: () => Navigator.pop(dialogContext, true), child: const Text('Đồng ý')),
      ],
    ),
  );
  return ok ?? false;
}

/// Widget dùng chung: tải danh sách từ API rồi dựng giao diện (kéo xuống để làm mới)
class ListLoader extends StatefulWidget {
  final String path;
  final Widget Function(BuildContext, Map<String, dynamic>, VoidCallback reload) itemBuilder;
  final Widget Function(VoidCallback reload)? header;
  final String empty;
  const ListLoader({super.key, required this.path, required this.itemBuilder, this.header, this.empty = 'Chưa có dữ liệu'});
  @override
  State<ListLoader> createState() => _ListLoaderState();
}

class _ListLoaderState extends State<ListLoader> {
  late Future<dynamic> f = Api.get(widget.path);
  void reload() => setState(() => f = Api.get(widget.path));

  @override
  Widget build(BuildContext context) => FutureBuilder(
        future: f,
        builder: (c, snap) {
          if (snap.connectionState != ConnectionState.done) return const Center(child: CircularProgressIndicator());
          final items = snap.hasError ? <Map<String, dynamic>>[] : (snap.data as List).cast<Map<String, dynamic>>();
          return RefreshIndicator(
            onRefresh: () async => reload(),
            child: ListView(children: [
              if (widget.header != null) widget.header!(reload),
              if (snap.hasError) Padding(padding: const EdgeInsets.all(24), child: Text(errText(snap.error!))),
              if (!snap.hasError && items.isEmpty)
                Padding(padding: const EdgeInsets.all(32), child: Center(child: Text(widget.empty))),
              ...items.map((i) => widget.itemBuilder(c, i, reload)),
            ]),
          );
        },
      );
}

/// Ô hiển thị một số đo
class Metric extends StatelessWidget {
  final String label;
  final dynamic value;
  final String unit;
  const Metric(this.label, this.value, this.unit, {super.key});
  @override
  Widget build(BuildContext context) => Expanded(
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(children: [
              Text(label),
              Text('${value ?? '--'} $unit', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            ]),
          ),
        ),
      );
}

/// Biểu đồ đường đơn giản (tự vẽ, không cần thư viện ngoài).
/// values: các giá trị theo thời gian (cũ -> mới), null được bỏ qua.
class MiniLineChart extends StatelessWidget {
  final String title;
  final List<double?> values;
  final Color color;
  final String unit;
  const MiniLineChart({super.key, required this.title, required this.values, required this.color, required this.unit});

  @override
  Widget build(BuildContext context) {
    final pts = values.whereType<double>().toList();
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(title, style: const TextStyle(fontWeight: FontWeight.bold)),
          if (pts.isEmpty)
            const Padding(padding: EdgeInsets.all(16), child: Text('Chưa có dữ liệu'))
          else ...[
            Text('Thấp nhất ${pts.reduce(math.min).toStringAsFixed(1)} $unit · Cao nhất ${pts.reduce(math.max).toStringAsFixed(1)} $unit',
                style: const TextStyle(fontSize: 12)),
            const SizedBox(height: 8),
            SizedBox(height: 120, width: double.infinity, child: CustomPaint(painter: _LinePainter(values, color))),
          ],
        ]),
      ),
    );
  }
}

class _LinePainter extends CustomPainter {
  final List<double?> values;
  final Color color;
  _LinePainter(this.values, this.color);

  @override
  void paint(Canvas canvas, Size size) {
    final pts = values.whereType<double>().toList();
    if (pts.length < 2) return;
    final lo = pts.reduce(math.min), hi = pts.reduce(math.max);
    final range = (hi - lo).abs() < 0.001 ? 1.0 : hi - lo;
    final grid = Paint()..color = Colors.grey.shade300;
    for (var i = 0; i <= 2; i++) {
      final y = size.height * i / 2;
      canvas.drawLine(Offset(0, y), Offset(size.width, y), grid);
    }
    final path = Path();
    for (var i = 0; i < pts.length; i++) {
      final x = size.width * i / (pts.length - 1);
      final y = size.height - (pts[i] - lo) / range * size.height;
      if (i == 0) {
        path.moveTo(x, y);
      } else {
        path.lineTo(x, y);
      }
    }
    canvas.drawPath(path, Paint()
      ..color = color
      ..strokeWidth = 2
      ..style = PaintingStyle.stroke);
  }

  @override
  bool shouldRepaint(covariant _LinePainter old) => old.values != values;
}
