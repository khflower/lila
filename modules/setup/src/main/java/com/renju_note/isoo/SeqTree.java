package com.renju_note.isoo;

import java.io.Serializable;

public class SeqTree implements Serializable {
    private static final long serialVersionUID = 2765198187236704398L;

    private final Node head = new Node(-1, -1);
    private Node now;
    private final int[][] now_board = new int[15][15];
    private final String text_box = null;

    public SeqTree() {
        this.now = this.head;
    }

    public static class Node implements Serializable {
        private static final long serialVersionUID = -6403100239045118824L;

        private final int x;
        private final int y;
        private Node chlid;
        private Node next;
        private Node parent;
        private String text;
        private String boxText;

        public Node(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public Node getChild() {
            return chlid;
        }

        public void setChild(Node child) {
            this.chlid = child;
        }

        public Node getNext() {
            return next;
        }

        public void setNext(Node next) {
            this.next = next;
        }

        public Node getParent() {
            return parent;
        }

        public void setParent(Node parent) {
            this.parent = parent;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getBoxText() {
            return boxText;
        }

        public void setBoxText(String boxText) {
            this.boxText = boxText;
        }
    }

    public Node getHead() {
        return head;
    }

    public Node getNow() {
        return now;
    }

    public void setNow(Node now) {
        this.now = now;
    }

    public int[][] getNow_board() {
        return now_board;
    }

    public String getText_box() {
        return text_box;
    }

    public void clearNowBoard() {
        for (int x = 0; x < 15; x++) {
            for (int y = 0; y < 15; y++) {
                now_board[x][y] = 0;
            }
        }
    }
}
