part of 'home_tab.dart';

class _RecentAccessView {
  const _RecentAccessView({required this.uniqueItems});

  final List<RecentAccessItem> uniqueItems;
}

final recentAccessViewProvider = Provider<_RecentAccessView>((ref) {
  final items = ref.watch(recentAccessProvider.select((s) => s.items));
  return _RecentAccessView(uniqueItems: items.take(10).toList());
});
