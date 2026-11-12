import 'package:flutter/material.dart';

import '../api.dart';
import '../widgets.dart';

/// Thuê khay: chọn gói -> chọn 1 hoặc nhiều khay trống -> gửi đơn (chờ nhân viên duyệt)
class RentTab extends StatefulWidget {
  const RentTab({super.key});
  @override
  State<RentTab> createState() => _RentTabState();
}

class _RentTabState extends State<RentTab> {
  List<dynamic> packages = [], trays = [];
  int? packageId;
  final selected = <int>{}; // các khay đang được chọn
  bool loading = true;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    try {
      final p = await Api.get('/packages') as List;
      final t = await Api.get('/trays/available') as List;
      if (!mounted) return;
      setState(() {
        packages = p;
        trays = t;
        packageId ??= packages.isNotEmpty ? (packages.first['id'] as num).toInt() : null;
        loading = false;
      });
    } catch (e) {
      if (mounted) {
        setState(() => loading = false);
        toast(context, errText(e));
      }
    }
  }

  num get total {
    final p = packages.where((x) => x['id'] == packageId);
    return p.isEmpty ? 0 : (num.tryParse(p.first['price'].toString()) ?? 0) * selected.length;
  }

  Future<void> rent() async {
    if (!await confirmDialog(context, 'Gửi đơn thuê ${selected.length} khay, tổng ${fmtMoney(total)}?')) return;
    try {
      await Api.post('/rentals', {'tray_ids': selected.toList(), 'package_id': packageId});
      selected.clear();
      await load();
      if (mounted) toast(context, 'Đã gửi đơn thuê. Nhân viên sẽ duyệt và bạn sẽ nhận thông báo.');
    } catch (e) {
      if (mounted) toast(context, errText(e));
    }
  }

  @override
  Widget build(BuildContext context) {
    if (loading) return const Center(child: CircularProgressIndicator());
    return RefreshIndicator(
      onRefresh: load,
      child: ListView(padding: const EdgeInsets.all(12), children: [
        const Text('Chọn gói thuê', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
        ...packages.map((p) => RadioListTile<int>(
              value: (p['id'] as num).toInt(),
              groupValue: packageId,
              onChanged: (v) => setState(() => packageId = v),
              title: Text('${p['name']} - ${fmtMoney(p['price'])}'),
              subtitle: Text('${p['duration_days']} ngày / khay${p['description'] != null ? '\n${p['description']}' : ''}'),
            )),
        const SizedBox(height: 8),
        const Text('Chọn khay trống', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
        if (trays.isEmpty) const Padding(padding: EdgeInsets.all(16), child: Text('Hiện không còn khay trống')),
        ...trays.map((t) {
          final tid = (t['id'] as num).toInt();
          return CheckboxListTile(
            value: selected.contains(tid),
            onChanged: (v) => setState(() => v == true ? selected.add(tid) : selected.remove(tid)),
            title: Text('${t['code']}'),
            subtitle: Text('${t['room_name']} • ${t['mushroom_type'] ?? ''}'),
          );
        }),
        const SizedBox(height: 12),
        FilledButton(
          onPressed: selected.isEmpty || packageId == null ? null : rent,
          child: Text('Thuê ${selected.length} khay${selected.isEmpty ? '' : ' - ${fmtMoney(total)}'}'),
        ),
      ]),
    );
  }
}
