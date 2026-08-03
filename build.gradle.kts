// The root build deliberately holds no shared configuration. Cross-module concerns
// live in build-logic convention plugins, which each module applies explicitly.

tasks.register("checkAll") {
    group = "verification"
    description = "Builds and tests every module."
    dependsOn(subprojects.map { "${it.path}:check" })
}
