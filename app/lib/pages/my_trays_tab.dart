import 'package:flutter/material.dart';

import '../api.dart';
import '../widgets.dart';
import 'tray_detail_page.dart';

/// Khay của tôi: khay đang thuê + đơn chờ duyệt; nhập mã khay để liên kết khay được bàn giao
class MyTraysTab extends StatelessWidget {
  const MyTraysTab({super.key});

  Future<void> claim(BuildContext context, VoidCallback reload) async {
    final code = await askText(context, 'Nhập mã khay', hint: 'Vd: TRAY-A1-001');
    if (code == null || code.isEmpty) return;
    try {
      await Api.post('/rentals/claim', {'code': code});
      if (context.mounted) toast(context, 'Đã liên kết khay $code với tài khoản');
      reload();
    } catch (e) {
      if (context.mounted) toast(context, errText(e));
    }
  }

  @override
  Widget build(BuildContext context) => ListLoader(
        path: '/trays/mine',
        empty: 'Bạn chưa thuê khay nào. Vào tab "Thuê khay" hoặc nhập mã khay được bàn giao.',
        header: (reload) => Padding(
          padding: const EdgeInsets.all(12),
          child: Row(children: [
            const Expanded(child: Text('Khay của tôi', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold))),
            OutlinedButton.icon(
              icon: const Icon(Icons.qr_code),
              label: const Text('Nhập mã khay'),
              onPressed: () => claim(context, reload),
            ),
          ]),
        ),
        itemBuilder: (c, t, reload) {
          final pending = t['rental_status'] == 'pending';
          return Card(
            margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            child: ListTile(
              leading: Icon(pending ? Icons.hourglass_top : Icons.grass, color: pending ? Colors.orange : Colors.green),
              title: Text('${t['code']}  •  ${t['room_name']}'),
              subtitle: Text(pending
                  ? 'Đơn thuê đang chờ nhân viên duyệt'
                  : 'Giai đoạn: ${t['growth_stage'] ?? '-'}\n'
                      'Trạng thái: ${trayLabels[t['status']] ?? t['status']}  |  Hạn thuê: ${fmtDate(t['end_date'])}'),
              isThreeLine: !pending,
              trailing: pending ? null : const Icon(Icons.chevron_right),
              onTap: pending ? null : () => Navigator.push(c, MaterialPageRoute(builder: (_) => TrayDetailPage(tray: t))),
            ),
          );
        },
      );
}
