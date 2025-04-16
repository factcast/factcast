package org.factcast.schema.registry.cli.validation.validators.impl

//class ValidVersionFolderValidatorTest : StringSpec() {
//    val uut = ValidVersionFolderValidator()
//    val ctx = mockk<ConstraintValidatorContext>()
//
//    init {
//        "isValid" {
//            every { ctx.defaultConstraintMessageTemplate } returns "foo"
//
//            table(
//                headers("path", "validity"),
//                row(Paths.get("1"), true),
//                row(Paths.get("1.2"), false),
//                row(Paths.get("bar"), false)
//            ).forAll { path, valid ->
//                uut.isValid(path, ctx) shouldBe valid
//            }
//        }
//    }
//}
