package com.uwetrottmann.trakt.v2.entities;

import java.util.LinkedList;
import java.util.List;

public class SyncItems {

    public List<SyncMovie> movies;
    public List<SyncShow> shows;
    public List<SyncEpisode> episodes;
    public SyncItems movies(SyncMovie movie) {
        LinkedList<SyncMovie> list = new LinkedList<SyncMovie>();
        list.add(movie);
        return movies(list);
    }

    public SyncItems movies(List<SyncMovie> movies) {
        this.movies = movies;
        return this;
    }

    public SyncItems shows(SyncShow show) {
        LinkedList<SyncShow> list = new LinkedList<SyncShow>();
        list.add(show);
        return shows(list);
    }
    public SyncItems episodes(SyncEpisode episode) {
        LinkedList<SyncEpisode> list = new LinkedList<SyncEpisode>();
        list.add(episode);
        return episodes(list);
    }
    public SyncItems episodes(List<SyncEpisode> episodes) {
        this.episodes=episodes;
        return this;
    }
    public SyncItems shows(List<SyncShow> shows) {
        this.shows = shows;
        return this;
    }

}
