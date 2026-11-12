import 'package:flutter/material.dart';

import 'api.dart';
import 'pages/home_page.dart';
import 'pages/login_page.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Api.load();
  runApp(const MushroomApp());
}

class MushroomApp extends StatelessWidget {
  const MushroomApp({super.key});

  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'Nuôi nấm thông minh',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(colorSchemeSeed: Colors.green, useMaterial3: true),
        // Có token thì vào thẳng màn hình chính, chưa có thì tới đăng nhập
        home: Api.token == null ? const LoginPage() : const HomePage(),
      );
}
