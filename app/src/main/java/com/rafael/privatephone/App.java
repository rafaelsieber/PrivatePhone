package com.rafael.privatephone;

import android.app.Application;

/**
 * Começa a ler a agenda assim que o processo sobe: na maioria das vezes ele sobe
 * justamente porque uma chamada chegou, e cada milissegundo conta.
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ContactIndex.get(this).start();
        Guard.scheduleDaily(this);
    }
}
