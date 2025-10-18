import 'package:dio/dio.dart';
import 'package:retrofit_test/models/post_model.dart';

import 'api_client_service.dart';

class ApiClientServiceManuelImpl implements ApiClientService{
  final Dio dio;
  ApiClientServiceManuelImpl(this.dio);
  @override
  Future<PostModel> createPost(PostModel post) {
    return dio.post(
        "https://jsonplaceholder.typicode.com/posts",
        data: post.toJson()
    )
        .then((value) => PostModel.fromJson(value.data));
  }

  @override
  Future<void> deletePost(int id) {
    return dio.delete("https://jsonplaceholder.typicode.com/posts/$id");
  }

  @override
  Future<List<PostModel>> getPosts() {
    return dio.get("https://jsonplaceholder.typicode.com/posts")
        .then((value) => value.data.map((e) => PostModel.fromJson(e)).toList());
  }

  @override
  Future<PostModel> updatePost(int id, PostModel post) {
    return dio.put(
        "https://jsonplaceholder.typicode.com/posts/$id",
        data: post.toJson()
    )
        .then((value) => PostModel.fromJson(value.data));
  }
}