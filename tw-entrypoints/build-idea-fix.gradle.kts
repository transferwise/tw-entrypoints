gradle.taskGraph.whenReady {
    val task = this.allTasks.find { it.name.endsWith(".main()") } as? JavaExec // or whatever other method your Main class runs
    task?.let {
        it.setExecutable(it.javaLauncher.get().executablePath.asFile.absolutePath)
    }
}
