
import 'package:flutter/foundation.dart';
import 'package:logger/logger.dart';

import '../models/post_model.dart';
import '../services/api_client_service.dart';
import '../services/rest_client.dart';
class PostViewModel extends ChangeNotifier {
  bool isLoadingPosts = true;
  List<PostModel> posts = [];
  PostModel? selectedPost ;
  final logger = Logger();


  void getPosts() async {
    try {
      final posts = await restClient.getPosts();
      this.posts = posts;
      logger.d(posts);
    } catch (e) {
      logger.e(e);
    }finally {
      isLoadingPosts = false;
      notifyListeners();
    }
  }

  void deletePost(int id) async {
    try {
      await restClient.deletePost(id);
      posts.removeWhere((post) => post.id == id);
      notifyListeners();
    } catch (e) {
      logger.e(e);
    }
  }

  void createPost(PostModel post) async {
    try {
      var postModel = await restClient.createPost(post);
      posts.add(postModel);
      notifyListeners();
    } catch (e) {
      logger.e(e);
    }
  }

  void updatePost(int id, PostModel post) async {
    try {
      var postModel = await restClient.updatePost(id, post);
      posts.removeWhere((post) => post.id == id);
      posts.add(postModel);
      notifyListeners();
    }catch (e) {
      logger.e(e);
    }
  }
}