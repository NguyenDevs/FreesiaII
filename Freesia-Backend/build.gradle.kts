repositories {
    maven("https://maven.citizensnpcs.co/repo")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("io.netty:netty-all:4.1.118.Final")
    compileOnly("net.citizensnpcs:citizens-main:2.0.41-SNAPSHOT") {
        exclude(group = "*", module = "*")
    }
}

tasks.build {
    dependsOn(tasks.named("shadowJar"))
}
