import 'package:dio/dio.dart';

import 'api_client_service.dart';
import 'package:logger/logger.dart';


final restClient = ApiClientService(dio);
final dio = getDio();

Dio getDio() {
  var logger = Logger();
  BaseOptions options = BaseOptions(
    receiveDataWhenStatusError: true,
    connectTimeout: const Duration(seconds: 30),
    receiveTimeout: const Duration(seconds: 30),
    baseUrl: "https://jsonplaceholder.typicode.com/",
    contentType: Headers.jsonContentType,
  );

  Dio dio = Dio(options);
  dio.interceptors.add(
    InterceptorsWrapper(
      onRequest: (options, handler) {
        logger.d("Request: ${options.method} ${options.path} ${options.baseUrl}");
        handler.next(options);
      },
      onResponse: (response, handler) {
        logger.d("Response: ${response.statusCode} ${response.requestOptions.path}");
        logger.d("Response: ${response.data}");
        handler.next(response);
      },
      onError: (error, handler) {
        logger.e("Error: ${error.message} ${error.response?.statusCode} ${error.response?.requestOptions.path}");
        logger.e("Error: ${error.response?.data}");
        handler.next(error);
      },
    ),
  );

  return dio;
}
