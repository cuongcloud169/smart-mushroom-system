import 'package:flutter/material.dart';

import '../api.dart';
import '../widgets.dart';
import 'login_page.dart';

/// Tài khoản: hồ sơ, đổi mật khẩu, lịch sử thuê (hủy đơn), lịch sử giao dịch, đăng xuất
class AccountTab extends StatefulWidget {
  const AccountTab({super.key});
  @override
  State<AccountTab> createState() => _AccountTabState();
}

class _AccountTabState extends State<AccountTab> {
  Map<String, dynamic>? me;
  List<dynamic> rentals = [], txs = [];
  final name = TextEditingController(), phone = TextEditingController(), address = TextEditingController();

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    try {
      final m = await Api.get('/auth/me');
      final r = await Api.get('/rentals');
      final t = await Api.get('/rentals/transactions');
      if (!mounted) return;
      setState(() {
        me = m;
        rentals = r;
        txs = t;
        name.text = m['full_name'] ?? '';
        phone.text = m['phone'] ?? '';
        address.text = m['address'] ?? '';
      });
    } catch (e) {
      if (mounted) toast(context, errText(e));
    }
  }

  Future<void> saveProfile() async {
    try {
      await Api.put('/auth/me', {'full_name': name.text.trim(), 'phone': phone.text.trim(), 'address': address.text.trim()});
      if (mounted) toast(context, 'Đã lưu thông tin');
    } catch (e) {
      if (mounted) toast(context, errText(e));
    }
  }

  Future<void> changePassword() async {
    final oldCtl = TextEditingController(), newCtl = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Đổi mật khẩu'),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          TextField(controller: oldCtl, obscureText: true, decoration: const InputDecoration(labelText: 'Mật khẩu hiện tại')),
          TextField(controller: newCtl, obscureText: true, decoration: const InputDecoration(labelText: 'Mật khẩu mới (tối thiểu 8 ký tự)')),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogContext, false), child: const Text('Hủy')),
          FilledButton(onPressed: () => Navigator.pop(dialogContext, true), child: const Text('Đổi')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await Api.put('/auth/me/password', {'old_password': oldCtl.text, 'new_password': newCtl.text});
      if (mounted) toast(context, 'Đã đổi mật khẩu');
    } catch (e) {
      if (mounted) toast(context, errText(e));
    }
  }

  Future<void> cancelRental(Map r) async {
    if (!await confirmDialog(context, 'Hủy hợp đồng thuê khay ${r['tray_code']}?')) return;
    try {
      await Api.put('/rentals/${r['id']}/status', {'status': 'cancelled'});
      await load();
    } catch (e) {
      if (mounted) toast(context, errText(e));
    }
  }

  Future<void> logout() async {
    await Api.saveToken(null);
    if (mounted) {
      Navigator.pushAndRemoveUntil(context, MaterialPageRoute(builder: (_) => const LoginPage()), (_) => false);
    }
  }

  @override
  Widget build(BuildContext context) => RefreshIndicator(
        onRefresh: load,
        child: ListView(padding: const EdgeInsets.all(12), children: [
          Text(me?['email'] ?? '', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
          TextField(controller: name, decoration: const InputDecoration(labelText: 'Họ tên')),
          TextField(controller: phone, keyboardType: TextInputType.phone, decoration: const InputDecoration(labelText: 'Số điện thoại')),
          TextField(controller: address, decoration: const InputDecoration(labelText: 'Địa chỉ')),
          const SizedBox(height: 8),
          Row(children: [
            Expanded(child: FilledButton(onPressed: saveProfile, child: const Text('Lưu thông tin'))),
            const SizedBox(width: 8),
            Expanded(child: OutlinedButton(onPressed: changePassword, child: const Text('Đổi mật khẩu'))),
          ]),
          const Divider(height: 32),
          const Text('Lịch sử thuê', style: TextStyle(fontWeight: FontWeight.bold)),
          if (rentals.isEmpty) const Text('Chưa có'),
          ...rentals.map((r) => ListTile(
                dense: true,
                title: Text('${r['tray_code']} - ${r['package_name'] ?? ''}'),
                subtitle: Text('${rentalLabels[r['status']] ?? r['status']} • ${fmtDate(r['start_date'])} → ${fmtDate(r['end_date'])}'),
                trailing: r['status'] == 'pending' || r['status'] == 'active'
                    ? TextButton(onPressed: () => cancelRental(r), child: const Text('Hủy'))
                    : null,
              )),
          const Divider(),
          const Text('Lịch sử giao dịch', style: TextStyle(fontWeight: FontWeight.bold)),
          if (txs.isEmpty) const Text('Chưa có'),
          ...txs.map((t) => ListTile(
                dense: true,
                title: Text('${fmtMoney(t['amount'])} - ${t['tray_code'] ?? ''}'),
                subtitle: Text('${txLabels[t['status']] ?? t['status']} • ${fmtTime(t['created_at'])}'),
              )),
          const Divider(),
          OutlinedButton(onPressed: logout, child: const Text('Đăng xuất')),
        ]),
      );
}
