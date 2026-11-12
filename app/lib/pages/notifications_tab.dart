import 'package:flutter/material.dart';

import '../api.dart';
import '../widgets.dart';

/// Thông báo: tiến độ, cảnh báo môi trường, sẵn sàng thu hoạch, đơn thuê, giao nhận
class NotificationsTab extends StatelessWidget {
  final VoidCallback onRead;
  const NotificationsTab({super.key, required this.onRead});

  IconData icon(String? type) => switch (type) {
        'env_alert' => Icons.warning_amber,
        'harvest' => Icons.eco,
        'progress' => Icons.trending_up,
        'rental' || 'rental_expiry' => Icons.receipt_long,
        'delivery' => Icons.local_shipping,
        _ => Icons.info_outline,
      };

  @override
  Widget build(BuildContext context) => ListLoader(
        path: '/notifications',
        empty: 'Chưa có thông báo',
        header: (reload) => Align(
          alignment: Alignment.centerRight,
          child: TextButton(
            onPressed: () async {
              await Api.put('/notifications/read-all');
              reload();
              onRead();
            },
            child: const Text('Đánh dấu đã đọc tất cả'),
          ),
        ),
        itemBuilder: (c, n, reload) => ListTile(
          leading: Icon(icon(n['type']), color: n['type'] == 'env_alert' ? Colors.orange : null),
          title: Text(n['title'], style: TextStyle(fontWeight: n['is_read'] == true ? FontWeight.normal : FontWeight.bold)),
          subtitle: Text('${n['body'] ?? ''}\n${fmtTime(n['created_at'])}'),
          isThreeLine: true,
          onTap: n['is_read'] == true
              ? null
              : () async {
                  await Api.put('/notifications/${n['id']}/read');
                  reload();
                  onRead();
                },
        ),
      );
}
