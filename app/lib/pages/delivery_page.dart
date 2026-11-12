import 'package:flutter/material.dart';

import '../api.dart';
import '../widgets.dart';

/// Yêu cầu nhận nấm tại trang trại hoặc giao tận nơi + danh sách yêu cầu đã gửi
class DeliveryPage extends StatefulWidget {
  final int trayId;
  const DeliveryPage({super.key, required this.trayId});
  @override
  State<DeliveryPage> createState() => _DeliveryPageState();
}

class _DeliveryPageState extends State<DeliveryPage> {
  String type = 'pickup';
  final address = TextEditingController();
  final note = TextEditingController();
  List<dynamic> requests = [];

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    try {
      final r = await Api.get('/deliveries') as List;
      if (mounted) setState(() => requests = r.where((d) => d['tray_id'] == widget.trayId).toList());
    } catch (_) {}
  }

  Future<void> send() async {
    try {
      await Api.post('/deliveries', {
        'tray_id': widget.trayId,
        'type': type,
        'address': address.text.trim(),
        'note': note.text.trim(),
      });
      if (!mounted) return;
      toast(context, 'Đã gửi yêu cầu');
      note.clear();
      load();
    } catch (e) {
      if (mounted) toast(context, errText(e));
    }
  }

  Future<void> cancel(Map d) async {
    if (!await confirmDialog(context, 'Hủy yêu cầu này?')) return;
    try {
      await Api.put('/deliveries/${d['id']}/status', {'status': 'cancelled'});
      load();
    } catch (e) {
      if (mounted) toast(context, errText(e));
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('Nhận / giao nấm')),
        body: ListView(padding: const EdgeInsets.all(16), children: [
          SegmentedButton<String>(
            segments: const [
              ButtonSegment(value: 'pickup', label: Text('Nhận tại trang trại')),
              ButtonSegment(value: 'delivery', label: Text('Giao tận nơi')),
            ],
            selected: {type},
            onSelectionChanged: (s) => setState(() => type = s.first),
          ),
          if (type == 'delivery') TextField(controller: address, decoration: const InputDecoration(labelText: 'Địa chỉ giao')),
          TextField(controller: note, decoration: const InputDecoration(labelText: 'Ghi chú (thời gian mong muốn...)')),
          const SizedBox(height: 16),
          FilledButton(onPressed: send, child: const Text('Gửi yêu cầu')),
          const Divider(height: 32),
          const Text('Yêu cầu đã gửi', style: TextStyle(fontWeight: FontWeight.bold)),
          if (requests.isEmpty) const Text('Chưa có yêu cầu'),
          ...requests.map((d) => ListTile(
                title: Text(d['type'] == 'pickup' ? 'Nhận tại trang trại' : 'Giao tận nơi: ${d['address'] ?? ''}'),
                subtitle: Text('${deliveryLabels[d['status']] ?? d['status']} • ${fmtTime(d['created_at'])}'),
                trailing: d['status'] == 'pending'
                    ? TextButton(onPressed: () => cancel(d), child: const Text('Hủy'))
                    : null,
              )),
        ]),
      );
}
