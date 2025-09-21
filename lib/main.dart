import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:retrofit_test/models/post_model.dart';
import 'package:retrofit_test/services/api_client_service.dart';
import 'package:retrofit_test/services/rest_client.dart';
import 'package:retrofit_test/view/post_view.dart';
import 'package:retrofit_test/view_model/post_view_model.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MultiProvider(providers: [
      ChangeNotifierProvider(create: (context) => PostViewModel()),
    ],child: MaterialApp(
      title: 'Flutter Demo',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
      ),
      home: PostView(),
    ),);
  }
}

