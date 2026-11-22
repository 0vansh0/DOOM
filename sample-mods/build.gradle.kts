plugins { base }

val batteryGlow by tasks.registering(Copy::class) {
    from(layout.projectDirectory.file("releases/Battery-Glow-1.0.doommod"))
    rename { "community.batteryglow.doommod" }
    into(layout.buildDirectory.dir("bundled/examples"))
}

tasks.assemble { dependsOn(batteryGlow) }
