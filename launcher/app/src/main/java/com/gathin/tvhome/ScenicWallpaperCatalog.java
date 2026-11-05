package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Scenic Glass' bundled HD wallpapers. The originals stay in their upstream theme folders. */
final class ScenicWallpaperCatalog {
    static final class Group {
        final String id, label, commonsSearch;
        final List<Item> builtIn;

        Group(String id, String label, String commonsSearch, String... assets) {
            this.id = id;
            this.label = label;
            this.commonsSearch = commonsSearch;
            List<Item> list = new ArrayList<>();
            for (String asset : assets) list.add(new Item(id + "/" + asset, id, title(asset), asset, null,
                    "CleanYourTV bundled collection", ""));
            builtIn = Collections.unmodifiableList(list);
        }
    }

    static final class Item {
        final String id, group, name, asset, file, credit, source;

        Item(String id, String group, String name, String asset, String file, String credit, String source) {
            this.id = id;
            this.group = group;
            this.name = name;
            this.asset = asset;
            this.file = file;
            this.credit = credit;
            this.source = source;
        }
    }

    private static String title(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1).replaceAll("\\.[^.]+$", "")
                .replace('-', ' ').replace('_', ' ');
        if (name.matches("[0-9]+")) return "Photo " + Integer.parseInt(name);
        return name.replaceFirst("^[0-9]+\\s+", "");
    }

    // Do not duplicate these photos in drawable/ or the APK; assets already bundles all 17 themes.
    static final Group[] GROUPS = {
            new Group("nature", "Nature", "landscape forest nature high resolution photograph",
                    "nature-calm/wallpapers/001.jpg", "nature-calm/wallpapers/002.jpg",
                    "nature-calm/wallpapers/003.jpg", "nature-calm/wallpapers/004.jpg",
                    "nature-calm/wallpapers/005.jpg", "nature-calm/wallpapers/006.jpg",
                    "nature-calm/wallpapers/007.jpg", "nature-calm/wallpapers/008.jpg",
                    "nature-calm/wallpapers/009.jpg", "nature-calm/wallpapers/010.jpg",
                    "nature-calm/wallpapers/011.jpg", "nature-calm/wallpapers/012.jpg",
                    "scenes/wallpapers/08-tea-hills.jpg", "scenes/wallpapers/14-autumn-valley.jpg"),
            new Group("mountains", "Mountains & lakes", "mountain lake panorama high resolution landscape photograph",
                    "scenes/wallpapers/01-dusk-peaks.jpg", "scenes/wallpapers/03-moon-lake.jpg",
                    "scenes/wallpapers/06-above-the-clouds.jpg", "scenes/wallpapers/07-mirror-lake.jpg",
                    "scenes/wallpapers/09-alpenglow.jpg", "scenes/wallpapers/12-monsoon.jpg",
                    "sweden/wallpapers/001.jpg", "sweden/wallpapers/002.jpg", "sweden/wallpapers/003.jpg"),
            new Group("coast", "Oceans & coasts", "coast ocean aerial landscape 4k photograph",
                    "scenes/wallpapers/02-golden-coast.jpg", "scenes/wallpapers/05-dunes.jpg",
                    "scenes/wallpapers/13-lighthouse-coast.jpg"),
            new Group("cities", "Cities", "city skyline night panorama high resolution photograph",
                    "scenes/wallpapers/10-city-lights.jpg", "scenes/wallpapers/11-balloon-sunrise.jpg"),
            new Group("space", "Space & auroras", "night sky nebula milky way photograph 4k",
                    "scenes/wallpapers/04-aurora.jpg", "aurora/wallpaper.jpg", "scenes/wallpapers/03-moon-lake.jpg"),
            new Group("galaxies", "Galaxies", "spiral galaxy nebula stars NASA astronomy photograph"),
            new Group("abstract", "Abstract", "abstract colored gradient generative art 4k",
                    "glass/wallpaper.jpg", "red-rain/wallpaper.jpg", "cinematic/wallpaper.jpg"),
            new Group("storms", "Storms & atmosphere", "dramatic storm thunderclouds landscape 4k photograph",
                    "storm/wallpapers/001.jpg", "storm/wallpapers/002.jpg",
                    "storm/wallpapers/003.jpg", "storm/wallpapers/004.jpg"),
            new Group("illustration", "Illustrations", "landscape digital illustration wallpaper",
                    "blocky/wallpapers/01-meadow-day.png", "blocky/wallpapers/02-meadow-dusk.png",
                    "blocky/wallpapers/03-meadow-night.png", "blocky/wallpapers/04-plains.png",
                    "blocky/wallpapers/05-desert.png", "blocky/wallpapers/06-snowy-forest.png",
                    "blocky/wallpapers/07-jungle.png", "blocky/wallpapers/08-ocean.png",
                    "blocky/wallpapers/09-blossom-grove.png", "blocky/wallpapers/10-cave.png",
                    "blocky/wallpapers/11-lava-cavern.png")
    };

    /** Country > cities as a nested collection; these photos are downloaded by the user (never silently). */
    static final Group[] BRAZIL = {
            new Group("brazil_fortaleza", "Fortaleza & Ceará", "aerial view Fortaleza Ceará coastline Brazil 4k photograph"),
            new Group("brazil_salvador", "Salvador & Bahia", "aerial view Salvador Bahia Brazil panorama 4k photograph"),
            new Group("brazil_rio", "Rio de Janeiro", "aerial view Rio de Janeiro Brazil city 4k photograph"),
            new Group("brazil_minas", "Minas Gerais", "aerial view Minas Gerais mountains Brazil 4k photograph"),
            new Group("brazil_brasilia", "Brasília", "aerial view Brasilia Distrito Federal Brazil 4k photograph"),
            new Group("brazil_sao_paulo", "São Paulo", "aerial panorama Sao Paulo Brazil 4k photograph"),
            new Group("brazil_recife", "Recife & Pernambuco", "aerial view Recife Pernambuco Brazil coastline 4k photograph")
    };

    static List<Group> allGroups() {
        List<Group> groups = new ArrayList<>();
        Collections.addAll(groups, GROUPS);
        Collections.addAll(groups, BRAZIL);
        return groups;
    }

    static Group find(String id) {
        for (Group group : allGroups()) if (group.id.equals(id)) return group;
        return null;
    }

    /** Never select the same image twice in a row if the collection contains another. */
    static Item pick(List<Item> items, String previous, String fixed, Random random) {
        if (items.isEmpty()) return null;
        if (fixed != null && !fixed.isEmpty()) {
            for (Item item : items) if (fixed.equals(item.id)) return item;
        }
        if (items.size() == 1) return items.get(0);
        int offset = random.nextInt(items.size());
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get((offset + i) % items.size());
            if (!item.id.equals(previous)) return item;
        }
        return items.get(0);
    }

    private ScenicWallpaperCatalog() { }
}
