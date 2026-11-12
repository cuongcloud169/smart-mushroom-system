import 'package:flutter/material.dart';

import '../api.dart';
import '../widgets.dart';
import 'home_page.dart';

/// Đăng nhập / đăng ký tài khoản khách hàng
class LoginPage extends StatefulWidget {
  const LoginPage({super.key});
  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final email = TextEditingController(text: 'customer@mushroom.local');
  final pass = TextEditingController(text: 'Customer@123');
  final name = TextEditingController();
  final phone = TextEditingController();
  bool register = false; // false = đăng nhập, true = đăng ký
  bool busy = false;

  Future<void> submit() async {
    setState(() => busy = true);
    try {
      final data = register
          ? await Api.post('/auth/register', {
              'email': email.text.trim(),
              'password': pass.text,
              'full_name': name.text.trim(),
              'phone': phone.text.trim(),
            })
          : await Api.post('/auth/login', {'email': email.text.trim(), 'password': pass.text});
      if (data['user']['role'] != 'customer') throw ApiError(403, 'Tài khoản quản trị vui lòng dùng trang web');
      await Api.saveToken(data['token']);
      if (!mounted) return;
      Navigator.pushReplacement(context, MaterialPageRoute(builder: (_) => const HomePage()));
    } catch (e) {
      if (mounted) toast(context, errText(e));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        body: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Column(children: [
              const Text('🍄', style: TextStyle(fontSize: 56)),
              Text(register ? 'Đăng ký' : 'Đăng nhập', style: Theme.of(context).textTheme.headlineSmall),
              const SizedBox(height: 16),
              if (register) TextField(controller: name, decoration: const InputDecoration(labelText: 'Họ tên')),
              if (register)
                TextField(controller: phone, keyboardType: TextInputType.phone,
                    decoration: const InputDecoration(labelText: 'Số điện thoại (không bắt buộc)')),
              TextField(controller: email, keyboardType: TextInputType.emailAddress, decoration: const InputDecoration(labelText: 'Email')),
              TextField(controller: pass, obscureText: true, decoration: const InputDecoration(labelText: 'Mật khẩu (tối thiểu 8 ký tự)')),
              const SizedBox(height: 16),
              FilledButton(onPressed: busy ? null : submit, child: Text(register ? 'Tạo tài khoản' : 'Đăng nhập')),
              TextButton(
                onPressed: () => setState(() => register = !register),
                child: Text(register ? 'Đã có tài khoản? Đăng nhập' : 'Chưa có tài khoản? Đăng ký'),
              ),
            ]),
          ),
        ),
      );
}
