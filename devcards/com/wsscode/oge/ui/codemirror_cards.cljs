(ns com.wsscode.oge.ui.codemirror-cards
  (:require [devcards.core :as dc :include-macros true]
            [cljs.test :refer [is testing]]
            [com.wsscode.oge.ui.codemirror :as cm]
            ["flatted/esm" :as flatted]))

(defn j [s]
  (flatted/parse s))

(def indexes
  {:com.wsscode.pathom.connect/index-io
   {#{} {:global {}}}})

(dc/deftest test-token-context
  (testing "| blank"
    (is (= (cm/token-context indexes (j " [{\"start\":0,\"end\":0,\"string\":\"1\",\"type\":null,\"state\":\"2\"},\"\",{\"pathStack\":\"3\",\"indentation\":0,\"mode\":null},{}]"))
           nil)))
  (testing "[|] root"
    (is (= (cm/token-context indexes (j " [{\"start\":0,\"end\":1,\"string\":\"1\",\"type\":\"2\",\"state\":\"3\"},\"[\",\"bracket\",{\"pathStack\":\"4\",\"indentation\":0,\"mode\":\"5\"},{\"mode\":\"5\",\"indent\":1,\"prev\":\"6\"},\"attr-list\",{}]"))
           {:type :attribute :context []})))
  (testing "[[|]] ident"
    (is (= (cm/token-context indexes (j " [{\"start\":1,\"end\":2,\"string\":\"1\",\"type\":\"2\",\"state\":\"3\"},\"[\",\"bracket\",{\"pathStack\":\"4\",\"indentation\":0,\"mode\":\"5\"},{\"mode\":\"5\",\"indent\":2,\"prev\":\"6\"},\"ident\",{\"mode\":\"7\",\"indent\":1,\"prev\":\"8\"},\"attr-list\",{}]"))
           {:type :ident})))
  (testing "[[:id|]] root typing ident"
    (is (= (cm/token-context indexes (j " [{\"start\":2,\"end\":5,\"string\":\"1\",\"type\":\"2\",\"state\":\"3\"},\":id\",\"atom-ident\",{\"pathStack\":\"4\",\"indentation\":0,\"mode\":\"5\"},{\"mode\":\"5\",\"indent\":2,\"prev\":\"6\",\"key\":\"1\"},\"ident\",{\"mode\":\"7\",\"indent\":1,\"prev\":\"8\"},\"attr-list\",{}]"))
           {:type :ident})))
  (testing "[[:ident |]] root ident value"
    (is (= (cm/token-context indexes (j " [{\"start\":8,\"end\":9,\"string\":\"1\",\"type\":null,\"state\":\"2\"},\" \",{\"pathStack\":\"3\",\"indentation\":0,\"mode\":\"4\"},{\"mode\":\"4\",\"indent\":2,\"prev\":\"5\",\"key\":\"6\"},\"ident\",{\"mode\":\"7\",\"indent\":1,\"prev\":\"8\"},\":ident\",\"attr-list\",{}]"))
           nil)))
  (testing "[{|}] attribute join key"
    (is (= (cm/token-context indexes (j " [{\"start\":1,\"end\":2,\"string\":\"1\",\"type\":\"2\",\"state\":\"3\"},\"{\",\"bracket\",{\"pathStack\":\"4\",\"indentation\":0,\"mode\":\"5\"},{\"mode\":\"5\",\"indent\":2,\"prev\":\"6\"},\"join\",{\"mode\":\"7\",\"indent\":1,\"prev\":\"8\"},\"attr-list\",{}]"))
           {:type :attribute :context []})))
  (testing "[{:jo|}] attribute join key typing"
    (is (= (cm/token-context indexes (j " [{\"start\":2,\"end\":5,\"string\":\"1\",\"type\":\"2\",\"state\":\"3\"},\":jo\",\"atom\",{\"pathStack\":\"4\",\"indentation\":0,\"mode\":\"5\"},{\"mode\":\"5\",\"indent\":2,\"prev\":\"6\",\"key\":\"1\"},\"join\",{\"mode\":\"7\",\"indent\":1,\"prev\":\"8\"},\"attr-list\",{}]"))
           {:type :attribute :context []})))
  (testing "[{:join [|]}] attribute join"
    (is (= (cm/token-context indexes (j " [{\"start\":8,\"end\":9,\"string\":\"1\",\"type\":\"2\",\"state\":\"3\"},\"[\",\"bracket\",{\"pathStack\":\"4\",\"indentation\":0,\"mode\":\"5\"},{\"mode\":\"5\",\"indent\":9,\"prev\":\"6\"},\"attr-list\",{\"mode\":\"7\",\"indent\":2,\"prev\":\"8\",\"key\":\"9\"},\"join\",{\"mode\":\"5\",\"indent\":1,\"prev\":\"10\"},\":join\",{}]"))
           {:type :attribute :context [:join]})))
  (testing "[{:join [{:foo [{:bar [|]}]}]}] nested attribute join"
    (is (= (cm/token-context indexes (j " [{\"start\":22,\"end\":23,\"string\":\"1\",\"type\":\"2\",\"state\":\"3\"},\"[\",\"bracket\",{\"pathStack\":\"4\",\"indentation\":0,\"mode\":\"5\"},{\"mode\":\"5\",\"indent\":23,\"prev\":\"6\"},\"attr-list\",{\"mode\":\"7\",\"indent\":17,\"prev\":\"8\",\"key\":\"9\"},\"join\",{\"mode\":\"5\",\"indent\":16,\"prev\":\"10\"},\":bar\",{\"mode\":\"7\",\"indent\":10,\"prev\":\"11\",\"key\":\"12\"},{\"mode\":\"5\",\"indent\":9,\"prev\":\"13\"},\":foo\",{\"mode\":\"7\",\"indent\":2,\"prev\":\"14\",\"key\":\"15\"},{\"mode\":\"5\",\"indent\":1,\"prev\":\"16\"},\":join\",{}]"))
           {:type :attribute :context [:bar :foo :join]})))
  (testing "[{:join [{[:ident 42] [|]}]}] ident resets context"
    (is (= (cm/token-context indexes (j " [{\"start\":22,\"end\":23,\"string\":\"1\",\"type\":\"2\",\"state\":\"3\"},\"[\",\"bracket\",{\"pathStack\":\"4\",\"indentation\":0,\"mode\":\"5\"},{\"mode\":\"5\",\"indent\":23,\"prev\":\"6\"},\"attr-list\",{\"mode\":\"7\",\"indent\":10,\"prev\":\"8\",\"key\":\"9\"},\"join\",{\"mode\":\"5\",\"indent\":9,\"prev\":\"10\"},{\"mode\":\"11\",\"indent\":11,\"prev\":\"6\",\"key\":\"12\"},{\"mode\":\"7\",\"indent\":2,\"prev\":\"13\",\"key\":\"14\"},\"ident\",\":ident\",{\"mode\":\"5\",\"indent\":1,\"prev\":\"15\"},\":join\",{}]"))
           {:type :attribute :context [:ident]})))
  (testing "[{:join [{:global [|]}]}] globals resets context"
    (is (= (cm/token-context indexes (j " [{\"start\":18,\"end\":19,\"string\":\"1\",\"type\":\"2\",\"state\":\"3\"},\"[\",\"bracket\",{\"pathStack\":\"4\",\"indentation\":0,\"mode\":\"5\"},{\"mode\":\"5\",\"indent\":19,\"prev\":\"6\"},\"attr-list\",{\"mode\":\"7\",\"indent\":10,\"prev\":\"8\",\"key\":\"9\"},\"join\",{\"mode\":\"5\",\"indent\":9,\"prev\":\"10\"},\":global\",{\"mode\":\"7\",\"indent\":2,\"prev\":\"11\",\"key\":\"12\"},{\"mode\":\"5\",\"indent\":1,\"prev\":\"13\"},\":join\",{}]"))
           {:type :attribute :context [:global]})))
  (testing "[{[:thing/id 42] [|]}] ident join query"
    (is (= (cm/token-context indexes (j " [{\"start\":17,\"end\":18,\"string\":\"1\",\"type\":\"2\",\"state\":\"3\"},\"[\",\"bracket\",{\"pathStack\":\"4\",\"indentation\":0,\"mode\":\"5\"},{\"mode\":\"5\",\"indent\":18,\"prev\":\"6\"},\"attr-list\",{\"mode\":\"7\",\"indent\":2,\"prev\":\"8\",\"key\":\"9\"},\"join\",{\"mode\":\"5\",\"indent\":1,\"prev\":\"10\"},{\"mode\":\"11\",\"indent\":3,\"prev\":\"6\",\"key\":\"12\"},{},\"ident\",\":thing/id\"]"))
           {:type :attribute :context [:thing/id]})))
  (testing "[(|)] param expression root"
    (is (= (cm/token-context indexes (j " [{\"start\":1,\"end\":2,\"string\":\"1\",\"type\":\"2\",\"state\":\"3\"},\"(\",\"bracket\",{\"pathStack\":\"4\",\"indentation\":0,\"mode\":\"5\"},{\"mode\":\"5\",\"indent\":2,\"prev\":\"6\"},\"param-exp\",{\"mode\":\"7\",\"indent\":1,\"prev\":\"8\"},\"attr-list\",{}]"))
           {:type :attribute :context []})))
  (testing "[(|)] param expression"
    (is (= (cm/token-context indexes (j " [{\"start\":1,\"end\":2,\"string\":\"1\",\"type\":\"2\",\"state\":\"3\"},\"(\",\"bracket\",{\"pathStack\":\"4\",\"indentation\":0,\"mode\":\"5\"},{\"mode\":\"5\",\"indent\":2,\"prev\":\"6\"},\"param-exp\",{\"mode\":\"7\",\"indent\":1,\"prev\":\"8\"},\"attr-list\",{}]"))
           {:type :attribute :context []})))
  (testing "[({|})] param expresion with join"
    (is (= (cm/token-context indexes (j " [{\"start\":2,\"end\":3,\"string\":\"1\",\"type\":\"2\",\"state\":\"3\"},\"{\",\"bracket\",{\"pathStack\":\"4\",\"indentation\":0,\"mode\":\"5\"},{\"mode\":\"5\",\"indent\":3,\"prev\":\"6\"},\"join\",{\"mode\":\"7\",\"indent\":2,\"prev\":\"8\"},\"param-exp\",{\"mode\":\"9\",\"indent\":1,\"prev\":\"10\"},\"attr-list\",{}]"))
           {:type :attribute :context []})))
  (testing "[({:foo [|]})] param expresion with join query"
    (is (= (cm/token-context indexes (j " [{\"start\":8,\"end\":9,\"string\":\"1\",\"type\":\"2\",\"state\":\"3\"},\"[\",\"bracket\",{\"pathStack\":\"4\",\"indentation\":0,\"mode\":\"5\"},{\"mode\":\"5\",\"indent\":9,\"prev\":\"6\"},\"attr-list\",{\"mode\":\"7\",\"indent\":3,\"prev\":\"8\",\"key\":\"9\"},\"join\",{\"mode\":\"10\",\"indent\":2,\"prev\":\"11\"},\":foo\",\"param-exp\",{\"mode\":\"5\",\"indent\":1,\"prev\":\"12\"},{}]"))
           {:type :attribute :context [:foo]})))
  (testing "[{(|)}] join with param expression"
    (is (= (cm/token-context indexes (j " [{\"start\":2,\"end\":3,\"string\":\"1\",\"type\":\"2\",\"state\":\"3\"},\"{\",\"bracket\",{\"pathStack\":\"4\",\"indentation\":0,\"mode\":\"5\"},{\"mode\":\"5\",\"indent\":3,\"prev\":\"6\"},\"join\",{\"mode\":\"7\",\"indent\":2,\"prev\":\"8\"},\"param-exp\",{\"mode\":\"9\",\"indent\":1,\"prev\":\"10\"},\"attr-list\",{}]"))
           {:type :attribute :context []})))
  (testing "[{(:foo {}) [|]}] join with param expression query"
    (is (= (cm/token-context indexes (j " [{\"start\":12,\"end\":13,\"string\":\"1\",\"type\":\"2\",\"state\":\"3\"},\"[\",\"bracket\",{\"pathStack\":\"4\",\"indentation\":0,\"mode\":\"5\"},{\"mode\":\"5\",\"indent\":13,\"prev\":\"6\"},\"attr-list\",{\"mode\":\"7\",\"indent\":2,\"prev\":\"8\",\"key\":\"9\"},\"join\",{\"mode\":\"5\",\"indent\":1,\"prev\":\"10\"},\":foo\",{}]"))
           {:type :attribute :context [:foo]})))
  (testing "[{:foo [({|})]}] join with param expression query"
    (is (= (cm/token-context indexes (j " [{\"start\":9,\"end\":10,\"string\":\"1\",\"type\":\"2\",\"state\":\"3\"},\"{\",\"bracket\",{\"pathStack\":\"4\",\"indentation\":0,\"mode\":\"5\"},{\"mode\":\"5\",\"indent\":10,\"prev\":\"6\"},\"join\",{\"mode\":\"7\",\"indent\":9,\"prev\":\"8\"},\"param-exp\",{\"mode\":\"9\",\"indent\":8,\"prev\":\"10\"},\"attr-list\",{\"mode\":\"5\",\"indent\":2,\"prev\":\"11\",\"key\":\"12\"},{\"mode\":\"9\",\"indent\":1,\"prev\":\"13\"},\":foo\",{}]"))
           {:type :attribute :context [:foo]})))
  (testing "[{:foo [{(:bar {}) [|]}]}] join with param expression query"
    (is (= (cm/token-context indexes (j " [{\"start\":19,\"end\":20,\"string\":\"1\",\"type\":\"2\",\"state\":\"3\"},\"[\",\"bracket\",{\"pathStack\":\"4\",\"indentation\":0,\"mode\":\"5\"},{\"mode\":\"5\",\"indent\":20,\"prev\":\"6\"},\"attr-list\",{\"mode\":\"7\",\"indent\":9,\"prev\":\"8\",\"key\":\"9\"},\"join\",{\"mode\":\"5\",\"indent\":8,\"prev\":\"10\"},\":bar\",{\"mode\":\"7\",\"indent\":2,\"prev\":\"11\",\"key\":\"12\"},{\"mode\":\"5\",\"indent\":1,\"prev\":\"13\"},\":foo\",{}]"))
           {:type :attribute :context [:bar :foo]})))
  (testing "[{[:ident 42] [{(|)}]}] join with param expression query"
    (is (= (cm/token-context indexes (j " [{\"start\":16,\"end\":17,\"string\":\"1\",\"type\":\"2\",\"state\":\"3\"},\"(\",\"bracket\",{\"pathStack\":\"4\",\"indentation\":0,\"mode\":\"5\"},{\"mode\":\"5\",\"indent\":17,\"prev\":\"6\"},\"param-exp\",{\"mode\":\"7\",\"indent\":16,\"prev\":\"8\"},\"join\",{\"mode\":\"9\",\"indent\":15,\"prev\":\"10\"},\"attr-list\",{\"mode\":\"7\",\"indent\":2,\"prev\":\"11\",\"key\":\"12\"},{\"mode\":\"9\",\"indent\":1,\"prev\":\"13\"},{\"mode\":\"14\",\"indent\":3,\"prev\":\"10\",\"key\":\"15\"},{},\"ident\",\":ident\"]"))
           {:type :attribute :context [:ident]}))))
