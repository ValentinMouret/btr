(ns btr.server
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [hiccup.core :as h]
   [muuntaja.core :as m]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as rrc]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [reitit.spec :as rs]
   [ring.adapter.jetty :as jetty])
  (:gen-class))

(def max-json-size-bytes
  "JSON’s which string representation is higher than `max-json-size-bytes`
  will not be recorded to the database.
  Completely arbirtary."
  4130)

(defn n-bytes
  [^String data]
  (-> data
      (.getBytes)
      count))

(defn size-ok?
  [data]
  (< (n-bytes (json/encode data)) max-json-size-bytes))

; (def ds (postgres/get-db))

;; Middleware

(def cors-headers
  {"Access-Control-Allow-Origin" "*"
   "Access-Control-Allow-Methods" "GET, PUT, PATCH, DELETE, POST, OPTIONS"
   "Access-Control-Allow-Headers" "Origin, X-Requested-With, Content-Type, Accept, Token"})

(def preflight-headers
  (merge cors-headers {"Connection" "keep-alive"}))

(defn preflight-request?
  [req]
  (-> req :request-method (= :options)))

(defn cors-middleware
  [handler]
  (fn [req]
    (if (preflight-request? req)
      {:status 204 ;; No Content
       :headers preflight-headers}
      (update-in (handler req) [:headers]
                 merge cors-headers))))

(defn read-body
  ([req]
   (read-body req true))
  ([req parse-keys?]
   (-> req
       :body
       slurp
       (json/parse-string parse-keys?))))

(defn page
  [title content]
  [:html {:lang "en"}
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width,initial-scale=1"}]
    [:link {:rel "stylesheet"
            :href "/style.css"}]        ; todo: add
    [:title title]]
   [:body content]])

(def index-page
  (page
   "btr"
   [:div.page-centered
    [:h1 "btr"]
    [:p "welcome to btr"]]))

(defn index-handler
  [_]
  {:status 200
   :body   (h/html index-page)})

(defn style-handler
  [_]
  (let [style-page (io/input-stream (io/resource "style.css"))]
    {:status 200
     :body   style-page}))

(def router
  (ring/router
   [["/" {:get {:handler index-handler}}]

    ["/index.html"
     {:get {:handler index-handler}}]

    ["/style.css"
     {:get {:handler style-handler}}]]

   {:data     {:muuntaja   m/instance
               :middleware [parameters/parameters-middleware
                            rrc/coerce-request-middleware
                            muuntaja/format-response-middleware
                            rrc/coerce-response-middleware]}
    :validate rs/validate}))

(def handler
  (ring/ring-handler
   router
   (ring/routes (ring/create-default-handler))
   {:middleware [cors-middleware]}))

(comment
  (defonce server
    (jetty/run-jetty #'handler {:port  8081
                                :join? false}))
      ;
  )

(defn -main
  [& args]
  (when (System/getenv "IS_PROD")
    (println "btr is running on production."))
  (jetty/run-jetty #'handler {:port  8080}))
