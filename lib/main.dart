import 'dart:io';
import 'dart:math' as math;
import 'dart:ui';

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:just_audio/just_audio.dart';
import 'package:just_audio_background/just_audio_background.dart';

import 'github_update_service.dart';
import 'models/music_models.dart';
import 'services/music_controller.dart';
import 'services/subsonic_service.dart';

const ink = Color(0xFF1D1D1F);
const mutedInk = Color(0xFF6E6E73);
const musicRed = Color(0xFFFA2D48);
const canvas = Color(0xFFF5F5F7);

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await JustAudioBackground.init(
    androidNotificationChannelId: 'io.github.admin0330.liquid_music.playback',
    androidNotificationChannelName: 'Liquid Music 播放',
    androidNotificationOngoing: false,
  );
  SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.dark,
      systemNavigationBarColor: Colors.transparent,
      systemNavigationBarIconBrightness: Brightness.dark,
    ),
  );
  runApp(const LiquidMusicApp());
}

class LiquidMusicApp extends StatefulWidget {
  const LiquidMusicApp({super.key});
  @override
  State<LiquidMusicApp> createState() => _LiquidMusicAppState();
}

class _LiquidMusicAppState extends State<LiquidMusicApp> {
  final controller = MusicController();

  @override
  void initState() {
    super.initState();
    controller.initialize();
  }

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => MaterialApp(
    debugShowCheckedModeBanner: false,
    title: 'Liquid Music',
    theme: ThemeData(
      brightness: Brightness.light,
      scaffoldBackgroundColor: canvas,
      colorScheme: ColorScheme.fromSeed(
        seedColor: musicRed,
        brightness: Brightness.light,
      ).copyWith(primary: musicRed, secondary: musicRed),
      fontFamily: 'sans-serif',
      useMaterial3: true,
      textTheme: ThemeData.light().textTheme.apply(
        bodyColor: ink,
        displayColor: ink,
      ),
      dividerColor: const Color(0x16000000),
      pageTransitionsTheme: const PageTransitionsTheme(
        builders: {TargetPlatform.android: CupertinoPageTransitionsBuilder()},
      ),
    ),
    home: MusicShell(controller: controller),
  );
}

class MusicShell extends StatefulWidget {
  const MusicShell({super.key, required this.controller});
  final MusicController controller;
  @override
  State<MusicShell> createState() => _MusicShellState();
}

class _MusicShellState extends State<MusicShell> {
  int tab = 0;

  void openPlayer() => showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    backgroundColor: Colors.transparent,
    builder: (_) => PlayerSheet(controller: widget.controller),
  );

  void openAlbum(MusicAlbum album) => Navigator.of(context).push(
    CupertinoPageRoute(
      builder: (_) => AlbumScreen(controller: widget.controller, album: album),
    ),
  );

  void openPlaylist(MusicPlaylist playlist) => Navigator.of(context).push(
    CupertinoPageRoute(
      builder: (_) =>
          PlaylistScreen(controller: widget.controller, playlist: playlist),
    ),
  );

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: widget.controller,
    builder: (context, _) {
      final bottom = widget.controller.playback.current == null ? 82.0 : 154.0;
      return Scaffold(
        extendBody: true,
        body: Stack(
          children: [
            const Positioned.fill(child: FrostedBackground()),
            IndexedStack(
              index: tab,
              children: [
                HomePage(
                  controller: widget.controller,
                  bottomPadding: bottom,
                  onAlbum: openAlbum,
                  onPlayer: openPlayer,
                ),
                LibraryPage(
                  controller: widget.controller,
                  bottomPadding: bottom,
                  onAlbum: openAlbum,
                  onPlaylist: openPlaylist,
                ),
                SearchPage(
                  controller: widget.controller,
                  bottomPadding: bottom,
                  onAlbum: openAlbum,
                ),
                SettingsPage(
                  controller: widget.controller,
                  bottomPadding: bottom,
                ),
              ],
            ),
            if (widget.controller.playback.current != null)
              Positioned(
                left: 10,
                right: 10,
                bottom: 76,
                child: MiniPlayer(
                  controller: widget.controller,
                  onTap: openPlayer,
                ),
              ),
            Positioned(
              left: 12,
              right: 12,
              bottom: 5,
              child: SafeArea(
                top: false,
                child: TelegramDock(
                  selectedIndex: tab,
                  onSelected: (value) => setState(() => tab = value),
                ),
              ),
            ),
          ],
        ),
      );
    },
  );
}

class TelegramDock extends StatelessWidget {
  const TelegramDock({
    super.key,
    required this.selectedIndex,
    required this.onSelected,
  });

  final int selectedIndex;
  final ValueChanged<int> onSelected;

  static const _items = <({IconData icon, IconData selected, String label})>[
    (
      icon: CupertinoIcons.play_circle,
      selected: CupertinoIcons.play_circle_fill,
      label: '主页',
    ),
    (
      icon: CupertinoIcons.music_albums,
      selected: CupertinoIcons.music_albums_fill,
      label: '资料库',
    ),
    (
      icon: CupertinoIcons.search,
      selected: CupertinoIcons.search_circle_fill,
      label: '搜索',
    ),
    (
      icon: CupertinoIcons.gear,
      selected: CupertinoIcons.gear_solid,
      label: '设置',
    ),
  ];

  @override
  Widget build(BuildContext context) => ClipRRect(
    borderRadius: BorderRadius.circular(28),
    child: BackdropFilter(
      filter: ImageFilter.blur(sigmaX: 24, sigmaY: 24),
      child: Container(
        height: 58,
        padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 4),
        decoration: BoxDecoration(
          color: const Color(0xFFFDFDFE).withValues(alpha: .82),
          borderRadius: BorderRadius.circular(28),
          border: Border.all(color: Colors.white.withValues(alpha: .95)),
          boxShadow: const [
            BoxShadow(
              color: Color(0x1C000000),
              blurRadius: 24,
              offset: Offset(0, 8),
            ),
          ],
        ),
        child: Row(
          children: List.generate(_items.length, (index) {
            final item = _items[index];
            final selected = index == selectedIndex;
            return Expanded(
              child: Semantics(
                selected: selected,
                button: true,
                label: item.label,
                child: InkWell(
                  onTap: () => onSelected(index),
                  borderRadius: BorderRadius.circular(22),
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 220),
                    curve: Curves.easeOutCubic,
                    decoration: BoxDecoration(
                      color: selected
                          ? const Color(0x14FA2D48)
                          : Colors.transparent,
                      borderRadius: BorderRadius.circular(22),
                    ),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          selected ? item.selected : item.icon,
                          color: selected ? musicRed : mutedInk,
                          size: 22,
                        ),
                        const SizedBox(height: 1),
                        Text(
                          item.label,
                          style: TextStyle(
                            color: selected ? musicRed : mutedInk,
                            fontSize: 10,
                            height: 1,
                            fontWeight: selected
                                ? FontWeight.w700
                                : FontWeight.w500,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            );
          }),
        ),
      ),
    ),
  );
}

class FrostedBackground extends StatefulWidget {
  const FrostedBackground({super.key});
  @override
  State<FrostedBackground> createState() => _FrostedBackgroundState();
}

class _FrostedBackgroundState extends State<FrostedBackground>
    with SingleTickerProviderStateMixin {
  late final AnimationController motion = AnimationController(
    vsync: this,
    duration: const Duration(seconds: 16),
  )..repeat(reverse: true);
  @override
  void dispose() {
    motion.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: motion,
    builder: (_, _) => CustomPaint(
      painter: BackgroundPainter(motion.value),
      child: const SizedBox.expand(),
    ),
  );
}

class BackgroundPainter extends CustomPainter {
  BackgroundPainter(this.t);
  final double t;
  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawRect(Offset.zero & size, Paint()..color = canvasColor);
    void glow(Offset center, double radius, Color color) => canvas.drawCircle(
      center,
      radius,
      Paint()
        ..shader = RadialGradient(
          colors: [color.withValues(alpha: .24), color.withValues(alpha: 0)],
        ).createShader(Rect.fromCircle(center: center, radius: radius))
        ..maskFilter = MaskFilter.blur(BlurStyle.normal, radius * .22),
    );
    glow(
      Offset(size.width * (.82 - .08 * t), size.height * .08),
      230,
      const Color(0xFFFF9AAF),
    );
    glow(
      Offset(size.width * (.1 + .08 * t), size.height * .62),
      210,
      const Color(0xFFFFD4C8),
    );
    glow(
      Offset(size.width * .86, size.height * (.78 - .05 * t)),
      190,
      const Color(0xFFBDE8F6),
    );
  }

  static const canvasColor = Color(0xFFFAFAFC);
  @override
  bool shouldRepaint(BackgroundPainter oldDelegate) => oldDelegate.t != t;
}

class GlassPanel extends StatelessWidget {
  const GlassPanel({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(16),
    this.radius = 26,
  });
  final Widget child;
  final EdgeInsets padding;
  final double radius;
  @override
  Widget build(BuildContext context) => ClipRRect(
    borderRadius: BorderRadius.circular(radius),
    child: BackdropFilter(
      filter: ImageFilter.blur(sigmaX: 28, sigmaY: 28),
      child: Container(
        padding: padding,
        decoration: BoxDecoration(
          color: Colors.white.withValues(alpha: .72),
          borderRadius: BorderRadius.circular(radius),
          border: Border.all(color: Colors.white.withValues(alpha: .9)),
          boxShadow: const [
            BoxShadow(
              color: Color(0x14000000),
              blurRadius: 30,
              offset: Offset(0, 10),
            ),
          ],
        ),
        child: child,
      ),
    ),
  );
}

class PageHeader extends StatelessWidget {
  const PageHeader(this.title, {super.key, this.trailing, this.subtitle});
  final String title;
  final String? subtitle;
  final Widget? trailing;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(20, 18, 16, 14),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (subtitle != null)
                Text(
                  subtitle!,
                  style: const TextStyle(
                    color: mutedInk,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              Text(
                title,
                style: const TextStyle(
                  fontSize: 34,
                  height: 1.1,
                  fontWeight: FontWeight.w700,
                  letterSpacing: -1.1,
                ),
              ),
            ],
          ),
        ),
        ?trailing,
      ],
    ),
  );
}

class HomePage extends StatelessWidget {
  const HomePage({
    super.key,
    required this.controller,
    required this.bottomPadding,
    required this.onAlbum,
    required this.onPlayer,
  });
  final MusicController controller;
  final double bottomPadding;
  final ValueChanged<MusicAlbum> onAlbum;
  final VoidCallback onPlayer;

  @override
  Widget build(BuildContext context) {
    final albums = [...controller.localAlbums, ...controller.remoteAlbums];
    final tracks = controller.allTracks;
    return SafeArea(
      bottom: false,
      child: RefreshIndicator(
        onRefresh: controller.refreshRemote,
        child: ListView(
          padding: EdgeInsets.only(bottom: bottomPadding),
          children: [
            PageHeader(
              '主页',
              subtitle: greeting(),
              trailing: IconButton.filledTonal(
                onPressed: () => controller.importLocal().then((count) {
                  if (context.mounted) {
                    message(context, count == 0 ? '没有选择音乐' : '已导入 $count 首歌曲');
                  }
                }),
                icon: const Icon(CupertinoIcons.add),
              ),
            ),
            if (controller.loading && albums.isEmpty)
              const Padding(
                padding: EdgeInsets.all(36),
                child: Center(child: CircularProgressIndicator()),
              ),
            if (!controller.loading && albums.isEmpty)
              EmptyLibrary(controller: controller),
            if (albums.isNotEmpty) ...[
              SectionTitle('最近添加', action: '查看资料库'),
              SizedBox(
                height: 224,
                child: ListView.separated(
                  padding: const EdgeInsets.symmetric(horizontal: 20),
                  scrollDirection: Axis.horizontal,
                  itemCount: math.min(albums.length, 12),
                  separatorBuilder: (_, _) => const SizedBox(width: 14),
                  itemBuilder: (_, index) => AlbumTile(
                    album: albums[index],
                    onTap: () => onAlbum(albums[index]),
                    onPlay: () async {
                      final album = await controller.loadAlbum(albums[index]);
                      await controller.playback.playTracks(album.tracks);
                    },
                  ),
                ),
              ),
            ],
            if (tracks.isNotEmpty) ...[
              const SectionTitle('为你推荐'),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: GlassPanel(
                  padding: const EdgeInsets.symmetric(vertical: 5),
                  child: Column(
                    children: [
                      for (var i = 0; i < math.min(tracks.length, 6); i++)
                        TrackRow(
                          track: tracks[i],
                          onTap: () => controller.playback.playTracks(
                            tracks,
                            initialIndex: i,
                          ),
                          onMore: () =>
                              showTrackActions(context, controller, tracks[i]),
                        ),
                    ],
                  ),
                ),
              ),
            ],
            if (controller.error != null)
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 20, 20, 0),
                child: Text(
                  controller.error!,
                  style: const TextStyle(color: musicRed),
                ),
              ),
          ],
        ),
      ),
    );
  }

  String greeting() {
    final hour = DateTime.now().hour;
    if (hour < 11) return '早上好';
    if (hour < 18) return '下午好';
    return '晚上好';
  }
}

class EmptyLibrary extends StatelessWidget {
  const EmptyLibrary({super.key, required this.controller});
  final MusicController controller;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(20, 24, 20, 16),
    child: GlassPanel(
      child: Column(
        children: [
          Container(
            width: 88,
            height: 88,
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                colors: [Color(0xFFFF375F), Color(0xFFBF5AF2)],
              ),
              borderRadius: BorderRadius.circular(26),
            ),
            child: const Icon(
              CupertinoIcons.music_note_2,
              color: Colors.white,
              size: 42,
            ),
          ),
          const SizedBox(height: 18),
          const Text(
            '开始建立你的音乐资料库',
            style: TextStyle(fontSize: 21, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 7),
          const Text(
            '导入手机中的 FLAC 等音频，或在设置中连接 Navidrome / Subsonic 服务器。',
            textAlign: TextAlign.center,
            style: TextStyle(color: mutedInk, height: 1.45),
          ),
          const SizedBox(height: 20),
          FilledButton.icon(
            onPressed: controller.importing
                ? null
                : () => controller.importLocal().then((count) {
                    if (context.mounted) message(context, '已导入 $count 首歌曲');
                  }),
            icon: controller.importing
                ? const SizedBox.square(
                    dimension: 17,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(CupertinoIcons.folder_open),
            label: const Text('导入本地音乐'),
          ),
        ],
      ),
    ),
  );
}

class LibraryPage extends StatefulWidget {
  const LibraryPage({
    super.key,
    required this.controller,
    required this.bottomPadding,
    required this.onAlbum,
    required this.onPlaylist,
  });
  final MusicController controller;
  final double bottomPadding;
  final ValueChanged<MusicAlbum> onAlbum;
  final ValueChanged<MusicPlaylist> onPlaylist;
  @override
  State<LibraryPage> createState() => _LibraryPageState();
}

class _LibraryPageState extends State<LibraryPage> {
  int filter = 0;
  static const labels = ['歌曲', '专辑', '收藏', '歌单', '已下载'];
  @override
  Widget build(BuildContext context) {
    final controller = widget.controller;
    final tracks = filter == 2
        ? controller.favorites
        : filter == 4
        ? controller.offlineTracks
        : controller.allTracks;
    final albums = [...controller.localAlbums, ...controller.remoteAlbums];
    final playlists = [
      ...controller.personalPlaylists,
      ...controller.remotePlaylists,
    ];
    return SafeArea(
      bottom: false,
      child: ListView(
        padding: EdgeInsets.only(bottom: widget.bottomPadding),
        children: [
          PageHeader(
            '资料库',
            trailing: IconButton(
              onPressed: () => createPlaylistDialog(context, controller),
              icon: const Icon(CupertinoIcons.add),
            ),
          ),
          SizedBox(
            height: 43,
            child: ListView.separated(
              padding: const EdgeInsets.symmetric(horizontal: 18),
              scrollDirection: Axis.horizontal,
              itemCount: labels.length,
              separatorBuilder: (_, _) => const SizedBox(width: 8),
              itemBuilder: (_, index) => ChoiceChip(
                label: Text(labels[index]),
                selected: filter == index,
                onSelected: (_) => setState(() => filter = index),
              ),
            ),
          ),
          const SizedBox(height: 15),
          if (filter == 1)
            AlbumGrid(albums: albums, onAlbum: widget.onAlbum)
          else if (filter == 3)
            PlaylistList(playlists: playlists, onTap: widget.onPlaylist)
          else if (tracks.isEmpty)
            const EmptyState(
              icon: CupertinoIcons.music_note_list,
              title: '这里还没有音乐',
              subtitle: '导入本地歌曲、收藏曲目或下载离线音乐后会显示在这里。',
            )
          else
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Column(
                children: [
                  for (var i = 0; i < tracks.length; i++)
                    TrackRow(
                      track: tracks[i],
                      onTap: () => controller.playback.playTracks(
                        tracks,
                        initialIndex: i,
                      ),
                      onMore: () =>
                          showTrackActions(context, controller, tracks[i]),
                    ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

class SearchPage extends StatefulWidget {
  const SearchPage({
    super.key,
    required this.controller,
    required this.bottomPadding,
    required this.onAlbum,
  });
  final MusicController controller;
  final double bottomPadding;
  final ValueChanged<MusicAlbum> onAlbum;
  @override
  State<SearchPage> createState() => _SearchPageState();
}

class _SearchPageState extends State<SearchPage> {
  final search = TextEditingController();
  @override
  void dispose() {
    search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final result = widget.controller.searchResult;
    return SafeArea(
      bottom: false,
      child: ListView(
        padding: EdgeInsets.only(bottom: widget.bottomPadding),
        children: [
          const PageHeader('搜索'),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 18),
            child: TextField(
              controller: search,
              onChanged: widget.controller.search,
              decoration: InputDecoration(
                filled: true,
                fillColor: Colors.white.withValues(alpha: .72),
                prefixIcon: const Icon(CupertinoIcons.search),
                suffixIcon: search.text.isEmpty
                    ? null
                    : IconButton(
                        onPressed: () {
                          search.clear();
                          widget.controller.search('');
                          setState(() {});
                        },
                        icon: const Icon(CupertinoIcons.clear_circled_solid),
                      ),
                hintText: '歌曲、艺人、专辑',
                border: OutlineInputBorder(
                  borderSide: BorderSide.none,
                  borderRadius: BorderRadius.circular(15),
                ),
              ),
            ),
          ),
          if (widget.controller.searching)
            const Padding(
              padding: EdgeInsets.all(26),
              child: Center(child: CircularProgressIndicator()),
            ),
          if (!widget.controller.searching && search.text.isEmpty)
            const EmptyState(
              icon: CupertinoIcons.search_circle,
              title: '搜索整个资料库',
              subtitle: '同时搜索本地歌曲和已连接的第三方音乐服务器。',
            ),
          if (result.albums.isNotEmpty) ...[
            const SectionTitle('专辑'),
            SizedBox(
              height: 224,
              child: ListView.separated(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                scrollDirection: Axis.horizontal,
                itemCount: result.albums.length,
                separatorBuilder: (_, _) => const SizedBox(width: 14),
                itemBuilder: (_, i) => AlbumTile(
                  album: result.albums[i],
                  onTap: () => widget.onAlbum(result.albums[i]),
                  onPlay: () async {
                    final album = await widget.controller.loadAlbum(
                      result.albums[i],
                    );
                    await widget.controller.playback.playTracks(album.tracks);
                  },
                ),
              ),
            ),
          ],
          if (result.tracks.isNotEmpty) ...[
            const SectionTitle('歌曲'),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Column(
                children: [
                  for (var i = 0; i < result.tracks.length; i++)
                    TrackRow(
                      track: result.tracks[i],
                      onTap: () => widget.controller.playback.playTracks(
                        result.tracks,
                        initialIndex: i,
                      ),
                      onMore: () => showTrackActions(
                        context,
                        widget.controller,
                        result.tracks[i],
                      ),
                    ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class SettingsPage extends StatefulWidget {
  const SettingsPage({
    super.key,
    required this.controller,
    required this.bottomPadding,
  });
  final MusicController controller;
  final double bottomPadding;
  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  final updates = GitHubUpdateService();
  bool checking = false;
  String version = '…';
  @override
  void initState() {
    super.initState();
    updates.currentVersion().then((v) {
      if (mounted) setState(() => version = v);
    });
  }

  Future<void> checkUpdate() async {
    if (checking) return;
    setState(() => checking = true);
    try {
      final result = await updates.check();
      if (!mounted) return;
      if (!result.hasUpdate) return message(context, '当前已是最新版本（v$version）');
      final install = await showModalBottomSheet<bool>(
        context: context,
        showDragHandle: true,
        builder: (_) => Padding(
          padding: const EdgeInsets.fromLTRB(24, 4, 24, 28),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                '发现新版本',
                style: TextStyle(fontSize: 24, fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 8),
              Text('v${result.currentVersion} → v${result.release!.version}'),
              if (result.release!.notes.trim().isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 14),
                  child: Text(
                    result.release!.notes,
                    maxLines: 7,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              const SizedBox(height: 20),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: () => Navigator.pop(context, true),
                  child: const Text('下载并安装'),
                ),
              ),
            ],
          ),
        ),
      );
      if (install == true) {
        final state = await updates.downloadAndInstall(result.release!.apkUrl);
        if (mounted) {
          message(
            context,
            state == InstallStartState.permissionRequired
                ? '请允许本应用安装未知应用，然后再次检查更新'
                : '正在后台下载，完成后将打开系统安装界面',
          );
        }
      }
    } catch (e) {
      if (mounted) message(context, '$e');
    } finally {
      if (mounted) setState(() => checking = false);
    }
  }

  @override
  Widget build(BuildContext context) => SafeArea(
    bottom: false,
    child: ListView(
      padding: EdgeInsets.only(bottom: widget.bottomPadding),
      children: [
        const PageHeader('设置'),
        SettingsGroup(
          title: '音乐来源',
          children: [
            SettingsTile(
              icon: CupertinoIcons.folder_fill,
              color: const Color(0xFF0A84FF),
              title: '导入本地音乐',
              subtitle:
                  '${widget.controller.localTracks.length} 首 · FLAC / ALAC / WAV / APE',
              onTap: () => widget.controller.importLocal().then((count) {
                if (context.mounted) message(context, '已导入 $count 首歌曲');
              }),
            ),
            SettingsTile(
              icon: CupertinoIcons.cloud_fill,
              color: musicRed,
              title: widget.controller.connected
                  ? 'Navidrome / Subsonic'
                  : '连接第三方音乐源',
              subtitle: widget.controller.connected
                  ? '${widget.controller.config!.username} · ${widget.controller.config!.normalizedServer}'
                  : 'Subsonic / OpenSubsonic',
              onTap: () => sourceDialog(context, widget.controller),
            ),
          ],
        ),
        SettingsGroup(
          title: '播放与存储',
          children: [
            SettingsTile(
              icon: CupertinoIcons.waveform,
              color: const Color(0xFFAF52DE),
              title: '原始音质播放',
              subtitle: '无损源不主动转码 · 支持后台与锁屏控制',
            ),
            SettingsTile(
              icon: Icons.usb_rounded,
              color: const Color(0xFF007AFF),
              title: 'USB 独占 / Bit-perfect',
              subtitle: widget.controller.usbAudioStatus.enabled
                  ? widget.controller.usbAudioStatus.detail
                  : widget.controller.usbAudioStatus.message,
              trailing: widget.controller.usbAudioBusy
                  ? const SizedBox.square(
                      dimension: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : Switch.adaptive(
                      value: widget.controller.usbAudioStatus.enabled,
                      onChanged: (value) async {
                        try {
                          final status = await widget.controller
                              .setUsbExclusive(value);
                          if (context.mounted) message(context, status.message);
                        } catch (e) {
                          if (context.mounted) message(context, '$e');
                        }
                      },
                    ),
              onTap: () async {
                await widget.controller.refreshUsbAudio();
                if (context.mounted) {
                  message(context, widget.controller.usbAudioStatus.message);
                }
              },
            ),
            SettingsTile(
              icon: CupertinoIcons.arrow_down_circle_fill,
              color: const Color(0xFF30B05A),
              title: '离线下载',
              subtitle: '${widget.controller.offlineTracks.length} 首已下载',
            ),
            SettingsTile(
              icon: CupertinoIcons.list_bullet,
              color: const Color(0xFFFF9F0A),
              title: '个人歌单',
              subtitle: '${widget.controller.personalPlaylists.length} 个',
            ),
          ],
        ),
        SettingsGroup(
          title: '应用',
          children: [
            SettingsTile(
              icon: CupertinoIcons.arrow_down_circle,
              color: const Color(0xFF5E5CE6),
              title: '检查 GitHub 更新',
              subtitle: '当前版本 v$version',
              trailing: checking
                  ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : null,
              onTap: checkUpdate,
            ),
            const SettingsTile(
              icon: CupertinoIcons.info_circle_fill,
              color: mutedInk,
              title: 'Liquid Music',
              subtitle: '非 Apple 官方产品 · 不包含商业音乐服务破解功能',
            ),
          ],
        ),
      ],
    ),
  );
}

class SettingsGroup extends StatelessWidget {
  const SettingsGroup({super.key, required this.title, required this.children});
  final String title;
  final List<Widget> children;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(16, 10, 16, 15),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(8, 0, 0, 8),
          child: Text(
            title,
            style: const TextStyle(
              color: mutedInk,
              fontSize: 13,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
        GlassPanel(
          padding: EdgeInsets.zero,
          radius: 20,
          child: Column(children: children),
        ),
      ],
    ),
  );
}

class SettingsTile extends StatelessWidget {
  const SettingsTile({
    super.key,
    required this.icon,
    required this.color,
    required this.title,
    required this.subtitle,
    this.onTap,
    this.trailing,
  });
  final IconData icon;
  final Color color;
  final String title;
  final String subtitle;
  final VoidCallback? onTap;
  final Widget? trailing;
  @override
  Widget build(BuildContext context) => ListTile(
    onTap: onTap,
    leading: Container(
      width: 34,
      height: 34,
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(9),
      ),
      child: Icon(icon, color: Colors.white, size: 20),
    ),
    title: Text(title, style: const TextStyle(fontWeight: FontWeight.w600)),
    subtitle: Text(subtitle, maxLines: 2, overflow: TextOverflow.ellipsis),
    trailing:
        trailing ??
        (onTap == null
            ? null
            : const Icon(
                CupertinoIcons.chevron_right,
                size: 17,
                color: mutedInk,
              )),
  );
}

class MiniPlayer extends StatelessWidget {
  const MiniPlayer({super.key, required this.controller, required this.onTap});
  final MusicController controller;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) {
    final playback = controller.playback;
    final track = playback.current!;
    return AnimatedBuilder(
      animation: playback,
      builder: (_, _) => GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onTap,
        child: GlassPanel(
          radius: 20,
          padding: const EdgeInsets.all(8),
          child: Row(
            children: [
              Artwork(track: track, size: 52, radius: 13),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      track.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontWeight: FontWeight.w700),
                    ),
                    Text(
                      track.artist,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(color: mutedInk, fontSize: 13),
                    ),
                  ],
                ),
              ),
              IconButton(
                onPressed: playback.toggle,
                icon: Icon(
                  playback.playing
                      ? CupertinoIcons.pause_fill
                      : CupertinoIcons.play_fill,
                ),
              ),
              IconButton(
                onPressed: playback.next,
                icon: const Icon(CupertinoIcons.forward_fill),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class PlayerSheet extends StatefulWidget {
  const PlayerSheet({super.key, required this.controller});
  final MusicController controller;
  @override
  State<PlayerSheet> createState() => _PlayerSheetState();
}

class _PlayerSheetState extends State<PlayerSheet> {
  bool showQueue = false;
  @override
  Widget build(BuildContext context) {
    final playback = widget.controller.playback;
    return AnimatedBuilder(
      animation: playback,
      builder: (_, _) {
        final track = playback.current;
        if (track == null) return const SizedBox.shrink();
        return SizedBox(
          height: MediaQuery.sizeOf(context).height,
          child: ClipRRect(
            borderRadius: const BorderRadius.vertical(top: Radius.circular(30)),
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 40, sigmaY: 40),
              child: Material(
                color: const Color(0xFFF3F3F5).withValues(alpha: .94),
                child: Column(
                  children: [
                    const SizedBox(height: 10),
                    Container(
                      width: 38,
                      height: 5,
                      decoration: BoxDecoration(
                        color: const Color(0x33000000),
                        borderRadius: BorderRadius.circular(9),
                      ),
                    ),
                    Expanded(
                      child: AnimatedSwitcher(
                        duration: const Duration(milliseconds: 250),
                        child: showQueue
                            ? queueView(playback)
                            : playerView(track, playback),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  Widget playerView(MusicTrack track, dynamic playback) =>
      SingleChildScrollView(
        key: const ValueKey('player'),
        padding: const EdgeInsets.fromLTRB(30, 24, 30, 28),
        child: Column(
          children: [
            LayoutBuilder(
              builder: (_, constraints) {
                final size = math.min(constraints.maxWidth, 360.0);
                return Artwork(
                  track: track,
                  size: size,
                  radius: 28,
                  shadow: true,
                );
              },
            ),
            const SizedBox(height: 28),
            Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        track.title,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: 23,
                          fontWeight: FontWeight.w700,
                          letterSpacing: -.4,
                        ),
                      ),
                      Text(
                        track.artist,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: 19,
                          color: musicRed,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  onPressed: () =>
                      showTrackActions(context, widget.controller, track),
                  icon: const Icon(
                    CupertinoIcons.ellipsis_circle_fill,
                    color: mutedInk,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            PositionSlider(playback: playback),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                IconButton(
                  onPressed: playback.previous,
                  iconSize: 36,
                  icon: const Icon(CupertinoIcons.backward_fill),
                ),
                ExpressivePlayButton(
                  playing: playback.playing,
                  onPressed: playback.toggle,
                  size: 82,
                ),
                IconButton(
                  onPressed: playback.next,
                  iconSize: 36,
                  icon: const Icon(CupertinoIcons.forward_fill),
                ),
              ],
            ),
            const SizedBox(height: 15),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
              decoration: BoxDecoration(
                color: track.isLossless
                    ? const Color(0x16FA2D48)
                    : const Color(0x0F000000),
                borderRadius: BorderRadius.circular(99),
              ),
              child: Text(
                '${track.isLossless ? '无损' : '音频'} · ${track.qualityLabel}',
                style: TextStyle(
                  color: track.isLossless ? musicRed : mutedInk,
                  fontWeight: FontWeight.w700,
                  fontSize: 12,
                ),
              ),
            ),
            const SizedBox(height: 18),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                PlayerAction(
                  icon: playback.shuffle
                      ? CupertinoIcons.shuffle_medium
                      : CupertinoIcons.shuffle,
                  active: playback.shuffle,
                  label: '随机',
                  onTap: playback.toggleShuffle,
                ),
                PlayerAction(
                  icon: CupertinoIcons.text_quote,
                  active: track.lyrics?.isNotEmpty == true,
                  label: '歌词',
                  onTap: () => lyricsSheet(context, widget.controller, track),
                ),
                PlayerAction(
                  icon: playback.loopMode == LoopMode.one
                      ? CupertinoIcons.repeat_1
                      : CupertinoIcons.repeat,
                  active: playback.loopMode != LoopMode.off,
                  label: '重复',
                  onTap: playback.cycleRepeat,
                ),
                PlayerAction(
                  icon: CupertinoIcons.list_bullet,
                  label: '队列',
                  onTap: () => setState(() => showQueue = true),
                ),
              ],
            ),
          ],
        ),
      );

  Widget queueView(dynamic playback) => Column(
    key: const ValueKey('queue'),
    children: [
      Padding(
        padding: const EdgeInsets.fromLTRB(18, 20, 10, 10),
        child: Row(
          children: [
            IconButton(
              onPressed: () => setState(() => showQueue = false),
              icon: const Icon(CupertinoIcons.chevron_down),
            ),
            const Expanded(
              child: Text(
                '接下来播放',
                style: TextStyle(fontSize: 22, fontWeight: FontWeight.w700),
              ),
            ),
          ],
        ),
      ),
      Expanded(
        child: ReorderableListView.builder(
          padding: const EdgeInsets.fromLTRB(12, 0, 12, 20),
          itemCount: playback.queue.length,
          onReorderItem: playback.reorderQueue,
          itemBuilder: (_, index) {
            final item = playback.queue[index];
            return TrackRow(
              key: ValueKey('${item.id}-$index'),
              track: item,
              selected: index == playback.currentIndex,
              onTap: () => playback.playAt(index),
              onMore: () => showTrackActions(context, widget.controller, item),
            );
          },
        ),
      ),
    ],
  );
}

class PositionSlider extends StatelessWidget {
  const PositionSlider({super.key, required this.playback});
  final dynamic playback;
  @override
  Widget build(BuildContext context) => StreamBuilder<Duration?>(
    stream: playback.durationStream,
    builder: (_, durationSnapshot) => StreamBuilder<Duration>(
      stream: playback.positionStream,
      builder: (_, positionSnapshot) {
        final duration =
            durationSnapshot.data ??
            playback.current?.duration ??
            Duration.zero;
        final position = positionSnapshot.data ?? Duration.zero;
        final max = math.max(1, duration.inMilliseconds).toDouble();
        final value = position.inMilliseconds.clamp(0, max.toInt()).toDouble();
        return Column(
          children: [
            Slider(
              value: value,
              max: max,
              onChanged: (v) =>
                  playback.seek(Duration(milliseconds: v.round())),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    formatDuration(position),
                    style: const TextStyle(color: mutedInk, fontSize: 11),
                  ),
                  Text(
                    '-${formatDuration(duration - position)}',
                    style: const TextStyle(color: mutedInk, fontSize: 11),
                  ),
                ],
              ),
            ),
          ],
        );
      },
    ),
  );
}

class PlayerAction extends StatelessWidget {
  const PlayerAction({
    super.key,
    required this.icon,
    required this.label,
    required this.onTap,
    this.active = false,
  });
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool active;
  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(14),
    child: Padding(
      padding: const EdgeInsets.all(8),
      child: Column(
        children: [
          Icon(icon, color: active ? musicRed : ink),
          const SizedBox(height: 4),
          Text(
            label,
            style: TextStyle(fontSize: 11, color: active ? musicRed : mutedInk),
          ),
        ],
      ),
    ),
  );
}

class AlbumScreen extends StatefulWidget {
  const AlbumScreen({super.key, required this.controller, required this.album});
  final MusicController controller;
  final MusicAlbum album;
  @override
  State<AlbumScreen> createState() => _AlbumScreenState();
}

class _AlbumScreenState extends State<AlbumScreen> {
  MusicAlbum? album;
  String? error;
  @override
  void initState() {
    super.initState();
    widget.controller
        .loadAlbum(widget.album)
        .then((value) {
          if (mounted) setState(() => album = value);
        })
        .catchError((e) {
          if (mounted) setState(() => error = '$e');
        });
  }

  @override
  Widget build(BuildContext context) {
    final value = album ?? widget.album;
    return Scaffold(
      body: Stack(
        children: [
          const Positioned.fill(child: FrostedBackground()),
          SafeArea(
            child: CustomScrollView(
              slivers: [
                SliverAppBar(
                  backgroundColor: Colors.transparent,
                  pinned: true,
                  title: Text(value.name),
                  actions: [
                    IconButton(
                      onPressed: () {},
                      icon: const Icon(CupertinoIcons.ellipsis_circle),
                    ),
                  ],
                ),
                SliverToBoxAdapter(
                  child: Column(
                    children: [
                      const SizedBox(height: 12),
                      Artwork(
                        album: value,
                        size: math.min(
                          MediaQuery.sizeOf(context).width - 96,
                          310,
                        ),
                        radius: 24,
                        shadow: true,
                      ),
                      const SizedBox(height: 20),
                      Text(
                        value.name,
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          fontSize: 25,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      Text(
                        value.artist,
                        style: const TextStyle(
                          fontSize: 18,
                          color: musicRed,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      Text(
                        [
                          if (value.year != null) '${value.year}',
                          '${value.songCount} 首歌曲',
                        ].join(' · '),
                        style: const TextStyle(color: mutedInk),
                      ),
                      const SizedBox(height: 18),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 18),
                        child: Row(
                          children: [
                            Expanded(
                              child: FilledButton.tonalIcon(
                                onPressed: value.tracks.isEmpty
                                    ? null
                                    : () => widget.controller.playback
                                          .playTracks(value.tracks),
                                icon: const Icon(CupertinoIcons.play_fill),
                                label: const Text('播放'),
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: FilledButton.tonalIcon(
                                onPressed: value.tracks.isEmpty
                                    ? null
                                    : () async {
                                        await widget.controller.playback
                                            .toggleShuffle();
                                        await widget.controller.playback
                                            .playTracks(value.tracks);
                                      },
                                icon: const Icon(CupertinoIcons.shuffle),
                                label: const Text('随机播放'),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 14),
                      if (album == null && error == null)
                        const CircularProgressIndicator(),
                      if (error != null)
                        Padding(
                          padding: const EdgeInsets.all(20),
                          child: Text(
                            error!,
                            style: const TextStyle(color: musicRed),
                          ),
                        ),
                      if (album != null)
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 12),
                          child: Column(
                            children: [
                              for (var i = 0; i < value.tracks.length; i++)
                                TrackRow(
                                  track: value.tracks[i],
                                  index: i + 1,
                                  onTap: () =>
                                      widget.controller.playback.playTracks(
                                        value.tracks,
                                        initialIndex: i,
                                      ),
                                  onMore: () => showTrackActions(
                                    context,
                                    widget.controller,
                                    value.tracks[i],
                                  ),
                                ),
                            ],
                          ),
                        ),
                      const SizedBox(height: 42),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class PlaylistScreen extends StatefulWidget {
  const PlaylistScreen({
    super.key,
    required this.controller,
    required this.playlist,
  });
  final MusicController controller;
  final MusicPlaylist playlist;
  @override
  State<PlaylistScreen> createState() => _PlaylistScreenState();
}

class _PlaylistScreenState extends State<PlaylistScreen> {
  MusicPlaylist? playlist;
  @override
  void initState() {
    super.initState();
    widget.controller.loadPlaylist(widget.playlist).then((v) {
      if (mounted) setState(() => playlist = v);
    });
  }

  @override
  Widget build(BuildContext context) {
    final value = playlist ?? widget.playlist;
    return Scaffold(
      appBar: AppBar(title: Text(value.name)),
      body: ListView(
        children: [
          const SizedBox(height: 20),
          Center(
            child: Artwork(
              playlist: value,
              size: 220,
              radius: 24,
              shadow: true,
            ),
          ),
          const SizedBox(height: 20),
          Center(
            child: Text(
              value.name,
              style: const TextStyle(fontSize: 25, fontWeight: FontWeight.w700),
            ),
          ),
          const SizedBox(height: 12),
          if (value.tracks.isNotEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 18),
              child: FilledButton.icon(
                onPressed: () =>
                    widget.controller.playback.playTracks(value.tracks),
                icon: const Icon(CupertinoIcons.play_fill),
                label: const Text('播放全部'),
              ),
            ),
          const SizedBox(height: 10),
          for (var i = 0; i < value.tracks.length; i++)
            TrackRow(
              track: value.tracks[i],
              onTap: () => widget.controller.playback.playTracks(
                value.tracks,
                initialIndex: i,
              ),
              onMore: () =>
                  showTrackActions(context, widget.controller, value.tracks[i]),
            ),
          if (playlist == null)
            const Padding(
              padding: EdgeInsets.all(30),
              child: Center(child: CircularProgressIndicator()),
            ),
        ],
      ),
    );
  }
}

class Artwork extends StatelessWidget {
  const Artwork({
    super.key,
    this.track,
    this.album,
    this.playlist,
    required this.size,
    this.radius = 16,
    this.shadow = false,
  });
  final MusicTrack? track;
  final MusicAlbum? album;
  final MusicPlaylist? playlist;
  final double size;
  final double radius;
  final bool shadow;
  @override
  Widget build(BuildContext context) {
    final value =
        track?.localCoverPath ??
        track?.coverUrl ??
        album?.coverUrl ??
        playlist?.coverUrl;
    Widget image;
    if (value == null || value.isEmpty) {
      image = Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFFFF6B81), Color(0xFFAF52DE), Color(0xFF5AC8FA)],
          ),
        ),
        child: Icon(
          playlist == null
              ? CupertinoIcons.music_note_2
              : CupertinoIcons.music_note_list,
          size: size * .34,
          color: Colors.white.withValues(alpha: .92),
        ),
      );
    } else {
      final uri = Uri.tryParse(value);
      image = uri != null && uri.scheme == 'file'
          ? Image.file(
              File.fromUri(uri),
              fit: BoxFit.cover,
              errorBuilder: (_, _, _) =>
                  const ColoredBox(color: Color(0xFFE5E5EA)),
            )
          : value.startsWith('/')
          ? Image.file(
              File(value),
              fit: BoxFit.cover,
              errorBuilder: (_, _, _) =>
                  const ColoredBox(color: Color(0xFFE5E5EA)),
            )
          : Image.network(
              value,
              fit: BoxFit.cover,
              errorBuilder: (_, _, _) =>
                  const ColoredBox(color: Color(0xFFE5E5EA)),
            );
    }
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(radius),
        boxShadow: shadow
            ? const [
                BoxShadow(
                  color: Color(0x33000000),
                  blurRadius: 30,
                  offset: Offset(0, 16),
                ),
              ]
            : null,
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(radius),
        child: image,
      ),
    );
  }
}

class TrackRow extends StatelessWidget {
  const TrackRow({
    super.key,
    required this.track,
    required this.onTap,
    required this.onMore,
    this.index,
    this.selected = false,
  });
  final MusicTrack track;
  final VoidCallback onTap;
  final VoidCallback onMore;
  final int? index;
  final bool selected;
  @override
  Widget build(BuildContext context) => ListTile(
    onTap: onTap,
    contentPadding: const EdgeInsets.symmetric(horizontal: 10, vertical: 2),
    leading: index == null
        ? Artwork(track: track, size: 48, radius: 11)
        : SizedBox(
            width: 30,
            child: Center(
              child: Text(
                '$index',
                style: TextStyle(color: selected ? musicRed : mutedInk),
              ),
            ),
          ),
    title: Text(
      track.title,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: TextStyle(
        fontWeight: FontWeight.w600,
        color: selected ? musicRed : ink,
      ),
    ),
    subtitle: Text(
      '${track.artist} · ${track.qualityLabel}',
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
    ),
    trailing: IconButton(
      onPressed: onMore,
      icon: const Icon(CupertinoIcons.ellipsis, size: 20),
    ),
  );
}

class AlbumTile extends StatelessWidget {
  const AlbumTile({
    super.key,
    required this.album,
    required this.onTap,
    required this.onPlay,
  });
  final MusicAlbum album;
  final VoidCallback onTap;
  final VoidCallback onPlay;
  @override
  Widget build(BuildContext context) => SizedBox(
    width: 174,
    child: GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: const Color(0xFFFFE8EC),
          borderRadius: BorderRadius.circular(32),
          boxShadow: const [
            BoxShadow(
              color: Color(0x12000000),
              blurRadius: 20,
              offset: Offset(0, 8),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                ClipPath(
                  clipper: const CookieClipper(),
                  child: Artwork(album: album, size: 92, radius: 0),
                ),
                const Spacer(),
                ExpressivePlayButton(
                  playing: false,
                  onPressed: onPlay,
                  size: 48,
                  compact: true,
                ),
              ],
            ),
            const Spacer(),
            Text(
              album.artist.toUpperCase(),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                color: musicRed,
                fontSize: 10,
                fontWeight: FontWeight.w800,
                letterSpacing: .7,
              ),
            ),
            const SizedBox(height: 3),
            Text(
              album.name,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontSize: 16,
                height: 1.05,
                fontWeight: FontWeight.w800,
                letterSpacing: -.35,
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

class ExpressivePlayButton extends StatefulWidget {
  const ExpressivePlayButton({
    super.key,
    required this.playing,
    required this.onPressed,
    this.size = 82,
    this.compact = false,
  });

  final bool playing;
  final VoidCallback onPressed;
  final double size;
  final bool compact;

  @override
  State<ExpressivePlayButton> createState() => _ExpressivePlayButtonState();
}

class _ExpressivePlayButtonState extends State<ExpressivePlayButton> {
  bool pressed = false;

  @override
  Widget build(BuildContext context) => Semantics(
    button: true,
    label: widget.playing ? '暂停' : '播放',
    child: GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTapDown: (_) => setState(() => pressed = true),
      onTapCancel: () => setState(() => pressed = false),
      onTapUp: (_) {
        setState(() => pressed = false);
        widget.onPressed();
      },
      child: AnimatedScale(
        scale: pressed ? .9 : 1,
        duration: const Duration(milliseconds: 130),
        curve: Curves.easeOutBack,
        child: AnimatedContainer(
          width: widget.size,
          height: widget.size,
          duration: const Duration(milliseconds: 340),
          curve: Curves.easeOutBack,
          decoration: BoxDecoration(
            color: widget.compact ? Colors.white : ink,
            borderRadius: BorderRadius.circular(
              widget.playing ? widget.size / 2 : widget.size * .31,
            ),
            boxShadow: const [
              BoxShadow(
                color: Color(0x22000000),
                blurRadius: 18,
                offset: Offset(0, 8),
              ),
            ],
          ),
          child: Icon(
            widget.playing
                ? CupertinoIcons.pause_fill
                : CupertinoIcons.play_fill,
            color: widget.compact ? ink : Colors.white,
            size: widget.size * .42,
          ),
        ),
      ),
    ),
  );
}

class CookieClipper extends CustomClipper<Path> {
  const CookieClipper();

  @override
  Path getClip(Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final outer = size.shortestSide * .51;
    final inner = size.shortestSide * .43;
    final points = <Offset>[];
    for (var i = 0; i < 14; i++) {
      final angle = -math.pi / 2 + i * math.pi / 7;
      final radius = i.isEven ? outer : inner;
      points.add(center + Offset(math.cos(angle), math.sin(angle)) * radius);
    }
    final path = Path();
    for (var i = 0; i < points.length; i++) {
      final current = points[i];
      final next = points[(i + 1) % points.length];
      final midpoint = Offset(
        (current.dx + next.dx) / 2,
        (current.dy + next.dy) / 2,
      );
      if (i == 0) path.moveTo(midpoint.dx, midpoint.dy);
      path.quadraticBezierTo(next.dx, next.dy, midpoint.dx, midpoint.dy);
    }
    return path..close();
  }

  @override
  bool shouldReclip(CookieClipper oldClipper) => false;
}

class AlbumGrid extends StatelessWidget {
  const AlbumGrid({super.key, required this.albums, required this.onAlbum});
  final List<MusicAlbum> albums;
  final ValueChanged<MusicAlbum> onAlbum;
  @override
  Widget build(BuildContext context) {
    if (albums.isEmpty) {
      return const EmptyState(
        icon: CupertinoIcons.music_albums,
        title: '还没有专辑',
        subtitle: '导入歌曲或连接音乐服务器后，专辑会自动整理。',
      );
    }
    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      padding: const EdgeInsets.symmetric(horizontal: 18),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        mainAxisSpacing: 16,
        crossAxisSpacing: 14,
        childAspectRatio: .78,
      ),
      itemCount: albums.length,
      itemBuilder: (_, i) => LayoutBuilder(
        builder: (_, constraints) => GestureDetector(
          onTap: () => onAlbum(albums[i]),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Artwork(album: albums[i], size: constraints.maxWidth, radius: 18),
              const SizedBox(height: 7),
              Text(
                albums[i].name,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontWeight: FontWeight.w600),
              ),
              Text(
                albums[i].artist,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(color: mutedInk, fontSize: 13),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class PlaylistList extends StatelessWidget {
  const PlaylistList({super.key, required this.playlists, required this.onTap});
  final List<MusicPlaylist> playlists;
  final ValueChanged<MusicPlaylist> onTap;
  @override
  Widget build(BuildContext context) => playlists.isEmpty
      ? const EmptyState(
          icon: CupertinoIcons.music_note_list,
          title: '还没有歌单',
          subtitle: '点击右上角加号创建个人歌单，服务器歌单也会显示在这里。',
        )
      : Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: Column(
            children: [
              for (final playlist in playlists)
                ListTile(
                  onTap: () => onTap(playlist),
                  leading: Artwork(playlist: playlist, size: 56, radius: 12),
                  title: Text(
                    playlist.name,
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
                  subtitle: Text('${playlist.songCount} 首歌曲'),
                  trailing: const Icon(CupertinoIcons.chevron_right, size: 18),
                ),
            ],
          ),
        );
}

class SectionTitle extends StatelessWidget {
  const SectionTitle(this.title, {super.key, this.action});
  final String title;
  final String? action;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(20, 24, 20, 12),
    child: Row(
      children: [
        Expanded(
          child: Text(
            title,
            style: const TextStyle(
              fontSize: 23,
              fontWeight: FontWeight.w700,
              letterSpacing: -.45,
            ),
          ),
        ),
        if (action != null)
          Text(action!, style: const TextStyle(color: musicRed)),
      ],
    ),
  );
}

class EmptyState extends StatelessWidget {
  const EmptyState({
    super.key,
    required this.icon,
    required this.title,
    required this.subtitle,
  });
  final IconData icon;
  final String title;
  final String subtitle;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.all(36),
    child: Column(
      children: [
        Icon(icon, size: 52, color: const Color(0x5577777A)),
        const SizedBox(height: 13),
        Text(
          title,
          style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w700),
        ),
        const SizedBox(height: 6),
        Text(
          subtitle,
          textAlign: TextAlign.center,
          style: const TextStyle(color: mutedInk, height: 1.4),
        ),
      ],
    ),
  );
}

Future<void> sourceDialog(
  BuildContext context,
  MusicController controller,
) async {
  final server = TextEditingController(
    text: controller.config?.serverUrl ?? '',
  );
  final user = TextEditingController(text: controller.config?.username ?? '');
  final password = TextEditingController();
  var busy = false;
  String? error;
  await showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (sheetContext) => StatefulBuilder(
      builder: (context, setSheetState) => Padding(
        padding: EdgeInsets.fromLTRB(
          22,
          4,
          22,
          22 + MediaQuery.viewInsetsOf(context).bottom,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              '第三方音乐源',
              style: TextStyle(fontSize: 24, fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 6),
            const Text(
              '支持 Navidrome、Subsonic 与兼容 OpenSubsonic 的自建服务器。建议使用 HTTPS。',
              style: TextStyle(color: mutedInk, height: 1.4),
            ),
            const SizedBox(height: 18),
            TextField(
              controller: server,
              keyboardType: TextInputType.url,
              decoration: const InputDecoration(
                labelText: '服务器地址',
                hintText: 'https://music.example.com',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: user,
              decoration: const InputDecoration(
                labelText: '用户名',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: password,
              obscureText: true,
              decoration: InputDecoration(
                labelText: controller.connected ? '密码（重新连接时填写）' : '密码',
                border: const OutlineInputBorder(),
              ),
            ),
            if (error != null)
              Padding(
                padding: const EdgeInsets.only(top: 10),
                child: Text(error!, style: const TextStyle(color: musicRed)),
              ),
            const SizedBox(height: 16),
            Row(
              children: [
                if (controller.connected)
                  TextButton(
                    onPressed: busy
                        ? null
                        : () async {
                            await controller.disconnect();
                            if (sheetContext.mounted) {
                              Navigator.pop(sheetContext);
                            }
                          },
                    child: const Text('断开连接'),
                  ),
                const Spacer(),
                FilledButton(
                  onPressed: busy
                      ? null
                      : () async {
                          setSheetState(() {
                            busy = true;
                            error = null;
                          });
                          try {
                            if (!server.text.trim().startsWith('http')) {
                              throw const SubsonicException(
                                '服务器地址必须以 http:// 或 https:// 开头',
                              );
                            }
                            if (user.text.trim().isEmpty ||
                                password.text.isEmpty) {
                              throw const SubsonicException('请输入用户名和密码');
                            }
                            await controller.connect(
                              SubsonicConfig(
                                serverUrl: server.text,
                                username: user.text.trim(),
                                password: password.text,
                              ),
                            );
                            if (sheetContext.mounted) {
                              Navigator.pop(sheetContext);
                            }
                          } catch (e) {
                            setSheetState(() {
                              error = '$e';
                              busy = false;
                            });
                          }
                        },
                  child: busy
                      ? const SizedBox.square(
                          dimension: 19,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Text('测试并连接'),
                ),
              ],
            ),
          ],
        ),
      ),
    ),
  );
  server.dispose();
  user.dispose();
  password.dispose();
}

Future<void> showTrackActions(
  BuildContext context,
  MusicController controller,
  MusicTrack track,
) async {
  await showModalBottomSheet<void>(
    context: context,
    showDragHandle: true,
    builder: (sheetContext) => SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          ListTile(
            leading: Artwork(track: track, size: 54, radius: 10),
            title: Text(
              track.title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontWeight: FontWeight.w700),
            ),
            subtitle: Text(track.artist),
          ),
          const Divider(),
          ListTile(
            leading: Icon(
              track.favorite ? CupertinoIcons.heart_fill : CupertinoIcons.heart,
              color: track.favorite ? musicRed : null,
            ),
            title: Text(track.favorite ? '取消收藏' : '收藏'),
            onTap: () async {
              Navigator.pop(sheetContext);
              try {
                await controller.toggleFavorite(track);
              } catch (e) {
                if (context.mounted) message(context, '$e');
              }
            },
          ),
          ListTile(
            leading: const Icon(CupertinoIcons.text_quote),
            title: const Text('查看歌词'),
            onTap: () {
              Navigator.pop(sheetContext);
              lyricsSheet(context, controller, track);
            },
          ),
          ListTile(
            leading: const Icon(CupertinoIcons.music_note_list),
            title: const Text('添加到歌单'),
            onTap: () {
              Navigator.pop(sheetContext);
              addToPlaylistSheet(context, controller, track);
            },
          ),
          if (track.source == MusicSourceKind.subsonic &&
              track.localPath == null)
            ListTile(
              leading: controller.downloadProgress.containsKey(track.id)
                  ? SizedBox.square(
                      dimension: 22,
                      child: CircularProgressIndicator(
                        value: controller.downloadProgress[track.id],
                      ),
                    )
                  : const Icon(CupertinoIcons.arrow_down_circle),
              title: const Text('下载原始音质'),
              onTap: controller.downloadProgress.containsKey(track.id)
                  ? null
                  : () async {
                      Navigator.pop(sheetContext);
                      try {
                        await controller.download(track);
                        if (context.mounted) {
                          message(context, '已下载 ${track.title}');
                        }
                      } catch (e) {
                        if (context.mounted) message(context, '$e');
                      }
                    },
            ),
          const SizedBox(height: 10),
        ],
      ),
    ),
  );
}

Future<void> lyricsSheet(
  BuildContext context,
  MusicController controller,
  MusicTrack track,
) => showModalBottomSheet<void>(
  context: context,
  isScrollControlled: true,
  showDragHandle: true,
  builder: (_) => DraggableScrollableSheet(
    expand: false,
    initialChildSize: .72,
    maxChildSize: .94,
    builder: (_, scroll) => ListView(
      controller: scroll,
      padding: const EdgeInsets.fromLTRB(24, 4, 24, 40),
      children: [
        Text(
          track.title,
          style: const TextStyle(fontSize: 25, fontWeight: FontWeight.w700),
        ),
        Text(
          track.artist,
          style: const TextStyle(color: musicRed, fontSize: 17),
        ),
        const SizedBox(height: 24),
        FutureBuilder<String?>(
          future: controller.lyricsFor(track),
          builder: (_, snapshot) {
            if (snapshot.connectionState != ConnectionState.done) {
              return const Center(child: CircularProgressIndicator());
            }
            return Text(
              snapshot.data?.trim().isNotEmpty == true
                  ? snapshot.data!
                  : '这首歌曲没有可用歌词。\n\n本地歌曲支持读取内嵌歌词；服务器歌词由 Subsonic 接口提供。',
              style: const TextStyle(
                fontSize: 20,
                height: 1.75,
                fontWeight: FontWeight.w600,
              ),
            );
          },
        ),
      ],
    ),
  ),
);

Future<void> createPlaylistDialog(
  BuildContext context,
  MusicController controller,
) async {
  final name = TextEditingController();
  final value = await showDialog<String>(
    context: context,
    builder: (_) => AlertDialog(
      title: const Text('新建歌单'),
      content: TextField(
        controller: name,
        autofocus: true,
        decoration: const InputDecoration(hintText: '歌单名称'),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('取消'),
        ),
        FilledButton(
          onPressed: () => Navigator.pop(context, name.text),
          child: const Text('创建'),
        ),
      ],
    ),
  );
  name.dispose();
  if (value != null) await controller.createPlaylist(value);
}

Future<void> addToPlaylistSheet(
  BuildContext context,
  MusicController controller,
  MusicTrack track,
) async {
  if (controller.personalPlaylists.isEmpty) {
    await controller.createPlaylist('我的歌单');
  }
  if (!context.mounted) return;
  await showModalBottomSheet<void>(
    context: context,
    showDragHandle: true,
    builder: (sheetContext) => SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const ListTile(
            title: Text(
              '添加到歌单',
              style: TextStyle(fontSize: 21, fontWeight: FontWeight.w700),
            ),
          ),
          for (final playlist in controller.personalPlaylists)
            ListTile(
              leading: Artwork(playlist: playlist, size: 48, radius: 10),
              title: Text(playlist.name),
              subtitle: Text('${playlist.tracks.length} 首歌曲'),
              onTap: () async {
                await controller.addToPlaylist(playlist, track);
                if (sheetContext.mounted) Navigator.pop(sheetContext);
              },
            ),
        ],
      ),
    ),
  );
}

void message(BuildContext context, String text) => ScaffoldMessenger.of(context)
  ..hideCurrentSnackBar()
  ..showSnackBar(SnackBar(content: Text(text)));

String formatDuration(Duration value) {
  if (value.isNegative) value = Duration.zero;
  final minutes = value.inMinutes;
  final seconds = value.inSeconds.remainder(60).toString().padLeft(2, '0');
  return '$minutes:$seconds';
}
