import 'dart:async';

import 'package:flutter/material.dart';

import '../api.dart';
import 'account_tab.dart';
import 'my_trays_tab.dart';
import 'notifications_tab.dart';
import 'rent_tab.dart';

/// Màn hình chính với 4 tab. Số thông báo chưa đọc được kiểm tra mỗi 30 giây.
class HomePage extends StatefulWidget {
  const HomePage({super.key});
  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  int tab = 0;
  int unread = 0;
  Timer? timer;

  Future<void> loadUnread() async {
    try {
      final r = await Api.get('/notifications/unread-count');
      if (mounted) setState(() => unread = (r['count'] as num).toInt());
    } catch (_) {}
  }

  @override
  void initState() {
    super.initState();
    loadUnread();
    timer = Timer.periodic(const Duration(seconds: 30), (_) => loadUnread());
  }

  @override
  void dispose() {
    timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final pages = [
      const MyTraysTab(),
      const RentTab(),
      NotificationsTab(onRead: loadUnread),
      const AccountTab(),
    ];
    return Scaffold(
      body: SafeArea(child: pages[tab]),
      bottomNavigationBar: NavigationBar(
        selectedIndex: tab,
        onDestinationSelected: (i) {
          setState(() => tab = i);
          loadUnread();
        },
        destinations: [
          const NavigationDestination(icon: Icon(Icons.grass), label: 'Khay của tôi'),
          const NavigationDestination(icon: Icon(Icons.add_shopping_cart), label: 'Thuê khay'),
          NavigationDestination(
            icon: Badge(isLabelVisible: unread > 0, label: Text('$unread'), child: const Icon(Icons.notifications)),
            label: 'Thông báo',
          ),
          const NavigationDestination(icon: Icon(Icons.person), label: 'Tài khoản'),
        ],
      ),
    );
  }
}
