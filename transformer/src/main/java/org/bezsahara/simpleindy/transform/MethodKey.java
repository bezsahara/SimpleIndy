package org.bezsahara.simpleindy.transform;

record MethodKey(String owner, String name, String descriptor) {
    String display() {
        return owner + "." + name + descriptor;
    }
}
