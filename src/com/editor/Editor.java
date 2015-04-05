/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.editor;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import javafx.concurrent.Worker.State;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import netscape.javascript.JSException;

/**
 *
 * @author DCaim
 */
public class Editor extends BorderPane {
    
    protected static final ExecutorService es = Executors.newFixedThreadPool(1);
    
    protected final WebView webview;
    private final ChangeListener<? super State> loadingState;
    private final Object lock;
    private boolean loaded;
    private boolean readOnly;
    
    public Editor() {
        super();
        this.webview = new WebView();
        this.lock = new Object();
        this.loaded = false;
        this.readOnly = false;
        
        setCenter(webview);
        
        readFile();
        
        loadingState = (v, v2, v3) -> {
            if(v3 == Worker.State.SUCCEEDED) {
                synchronized(lock) {
                    loaded = true;
                    lock.notifyAll();
                }
            }
        };
        //implement copy/paste feature
        webview.setOnKeyReleased(event -> {
            if(event.isControlDown()) {
                if(event.getCode() == KeyCode.C) {
                    final String copy = webview.getEngine().executeScript("editor.getCopyText();").toString();
                    StringSelection selection = new StringSelection(copy);
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    clipboard.setContents(selection, selection);
                } else if(event.getCode() == KeyCode.V) {
                    Toolkit toolkit = Toolkit.getDefaultToolkit();
                    Clipboard clipboard = toolkit.getSystemClipboard();
                    String copy = null;
                    try {
                        copy = clipboard.getData(DataFlavor.stringFlavor).toString();
                    } catch (UnsupportedFlavorException | IOException ex) {
                        Logger.getLogger(Editor.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    if(copy != null) {
                        if(copy.contains("\n"))
                            copy = generateJavascriptString(copy);
                        webview.getEngine().executeScript("editor.insert(\""+copy+"\")");
                    }
                }
            }
        });
        
        webview.getEngine().getLoadWorker().stateProperty().addListener(loadingState);
    }
    
    public void readFile() {
        webview.getEngine().load(getClass().getResource("/com/editor/neweditor/editor.html").toExternalForm());
    }
    
    /**
     * Write the text from a database source.
     * The text needs to be split with "\n".
     * Recommended to be launched from FX Application Thread, because the internal API 
     * instances a new Thread
     * @param str 
     */
    public void setText(final String str) {
        es.submit(() -> {
            final String strJs = generateJavascriptString(str);
            //synchronized method to wait for 
            waitForLoading();
            
            //sleep before setting value in the webview - prevent of unsupported bugs
            try {Thread.sleep(150);}catch(InterruptedException ex){}
            
            setDocText(strJs);
        });
    }
    
    public void setMode(final String mode) {
        es.submit(() -> {
            
            waitForLoading();
            
            Platform.runLater(() -> {
                webview.getEngine().executeScript("editor.setMode(\""+mode+"\");");
            });
        });
    }
    
    /**
     * To execute in the FX Application Thread.
     * @return 
     */
    public String getText() {
        return webview.getEngine().executeScript("editor.getValue();").toString();
    }
    
    public boolean isReadOnly() {
        return readOnly;
    }
    
    public void setReadOnly(boolean readOnly) {
        if(this.readOnly != readOnly) {
            es.submit(() -> {
                waitForLoading();
                Platform.runLater(() -> {
                   webview.getEngine().executeScript("editor.setReadOnly("+readOnly+");"); 
                });
                
                this.readOnly=readOnly;
            });
        }
    }
    
    public static void shutdown() {
        Editor.es.shutdownNow();
    }
    
    public void close() {
        webview.getEngine().getLoadWorker().stateProperty().removeListener(loadingState);
    }
    
    protected void waitForLoading() {
        while(!loaded) {
            synchronized(lock) {
                if(!loaded) {
                    try {
                        lock.wait();
                    } catch(Exception ex) {ex.printStackTrace();}
                }
            }
        }
    }
    
    protected String generateJavascriptString(final String str) {
        final StringBuilder sb = new StringBuilder();
            final String[] split = str.split("\n");
            for(String s : split)
                sb.append(s).append("\\n");
        return sb.toString();
    }
    
    protected void setDocText(final String text) {
        if(Platform.isFxApplicationThread()) {
            try {
                webview.getEngine().executeScript("editor.setValue(\""+text+"\");editor.clearSelection();editor.gotoLine(0,0,false);");
            } catch(JSException ex) {
                ex.printStackTrace();
            }
        } else
            Platform.runLater(() -> {setDocText(text);});
    }
    
}
