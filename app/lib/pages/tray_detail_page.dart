import 'dart:async';

import 'package:flutter/material.dart';

import '../api.dart';
import '../widgets.dart';
import 'delivery_page.dart';

/// Chi tiết khay: camera, nhiệt độ/độ ẩm/CO2 + biểu đồ, tiến độ, lịch thu hoạch, yêu cầu nhận/giao
class TrayDetailPage extends StatefulWidget {
  final Map<String, dynamic> tray;
  const TrayDetailPage({super.key, required this.tray});
  @override
  State<TrayDetailPage> createState() => _TrayDetailPageState();
}

class _TrayDetailPageState extends State<TrayDetailPage> {
  Map<String, dynamic>? detail;
  List<dynamic> readings = [];
  String? cameraUrl;
  String? error;
  Timer? dataTimer, cameraTimer;
  int frame = 0; // đổi số này để ép tải lại ảnh camera

  int get id => (widget.tray['id'] as num).toInt();

  Future<void> load() async {
    try {
      final d = await Api.get('/trays/$id/detail');
      final r = await Api.get('/trays/$id/readings?limit=60');
      final cams = await Api.get('/trays/$id/camera') as List;
      if (!mounted) return;
      setState(() {
        detail = d;
        readings = r;
        cameraUrl = cams.isNotEmpty ? cams.first['stream_url'] : null;
        error = null;
      });
    } catch (e) {
      if (mounted) setState(() => error = errText(e));
    }
  }

  @override
  void initState() {
    super.initState();
    load();
    // Dữ liệu môi trường làm mới mỗi 10 giây; ảnh camera làm mới mỗi 2 giây (gần thời gian thực)
    dataTimer = Timer.periodic(const Duration(seconds: 10), (_) => load());
    cameraTimer = Timer.periodic(const Duration(seconds: 2), (_) {
      if (mounted && cameraUrl != null) setState(() => frame++);
    });
  }

  @override
  void dispose() {
    dataTimer?.cancel();
    cameraTimer?.cancel();
    super.dispose();
  }

  double? _num(dynamic v) => v == null ? null : double.tryParse(v.toString());

  @override
  Widget build(BuildContext context) {
    final latest = readings.isNotEmpty ? readings.first : null;
    final series = readings.reversed.toList(); // cũ -> mới cho biểu đồ
    return Scaffold(
      appBar: AppBar(title: Text('Khay ${widget.tray['code']}')),
      body: RefreshIndicator(
        onRefresh: load,
        child: ListView(padding: const EdgeInsets.all(12), children: [
          if (error != null) Text(error!, style: const TextStyle(color: Colors.red)),
          // Camera: ảnh snapshot/MJPEG từ camera IP, làm mới mỗi 2 giây
          Card(
            clipBehavior: Clip.antiAlias,
            child: cameraUrl == null
                ? const SizedBox(height: 160, child: Center(child: Text('Khay chưa có camera')))
                : Image.network('$cameraUrl${cameraUrl!.contains('?') ? '&' : '?'}t=$frame',
                    gaplessPlayback: true,
                    errorBuilder: (_, __, ___) => const SizedBox(height: 160, child: Center(child: Text('Không kết nối được camera')))),
          ),
          Row(children: [
            Metric('Nhiệt độ', latest?['temperature'], '°C'),
            Metric('Độ ẩm', latest?['humidity'], '%'),
            Metric('CO₂', latest?['co2'], 'ppm'),
          ]),
          if (latest != null) Text('Cập nhật: ${fmtTime(latest['recorded_at'])}', style: const TextStyle(fontSize: 12)),
          MiniLineChart(title: 'Nhiệt độ gần đây', unit: '°C', color: Colors.deepOrange,
              values: series.map((x) => _num(x['temperature'])).toList()),
          MiniLineChart(title: 'Độ ẩm gần đây', unit: '%', color: Colors.blue,
              values: series.map((x) => _num(x['humidity'])).toList()),
          MiniLineChart(title: 'CO₂ gần đây', unit: 'ppm', color: Colors.green,
              values: series.map((x) => _num(x['co2'])).toList()),
          const SizedBox(height: 12),
          if (detail != null) ...[
            Text('Giai đoạn: ${detail!['growth_stage'] ?? '-'}', style: const TextStyle(fontWeight: FontWeight.bold)),
            Text('Trạng thái: ${trayLabels[detail!['status']] ?? detail!['status']}  |  Lấp đầy: ${detail!['fill_percent']}%'),
            Text('Dự kiến thu hoạch: ${detail!['expected_harvest_date'] == null ? 'chưa có' : fmtDate(detail!['expected_harvest_date'])}'),
            const Divider(),
            const Text('Lịch thu hoạch', style: TextStyle(fontWeight: FontWeight.bold)),
            if ((detail!['harvests'] as List).isEmpty) const Text('Chưa có lịch'),
            ...(detail!['harvests'] as List).map((h) => ListTile(
                  dense: true,
                  leading: const Icon(Icons.event),
                  title: Text('Ngày ${fmtDate(h['planned_date'])}'),
                  subtitle: Text(h['status'] == 'harvested'
                      ? 'Đã thu hoạch: ${h['yield_kg']} kg'
                      : h['status'] == 'cancelled' ? 'Đã hủy' : 'Đã lên lịch'),
                )),
            const Divider(),
            const Text('Nhật ký tiến độ', style: TextStyle(fontWeight: FontWeight.bold)),
            if ((detail!['progress_logs'] as List).isEmpty) const Text('Chưa có cập nhật'),
            ...(detail!['progress_logs'] as List).map((l) => ListTile(
                  dense: true,
                  title: Text('${l['growth_stage'] ?? ''} ${l['note'] ?? ''}'.trim()),
                  subtitle: Text(fmtTime(l['created_at'])),
                )),
            const Divider(),
            FilledButton.icon(
              icon: const Icon(Icons.local_shipping),
              label: const Text('Yêu cầu nhận / giao nấm'),
              onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => DeliveryPage(trayId: id))),
            ),
          ],
        ]),
      ),
    );
  }
}
