          .addPath("TestView/View.xml").addDatasourceName(DATASOURCE_NAME)
          .addPreviousCommitId("be77432d15dec81b4c60ed858d5d678ceb247171").build();
      List<String> diffs = vcs
          .getDiffEntries(DATASOURCE_NAME, "HEAD", "be77432d15dec81b4c60ed858d5d678ceb247171", "TestView/View.xml");
  private static Matcher<List<String>> matches(final String expected) {