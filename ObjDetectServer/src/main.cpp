
#include "objdetserver.h"

#define GPU
#define OPENCV
#include <darknet.h>
#include "opencv2/opencv.hpp"

extern "C"
{
    image mat_to_image(cv::Mat m);
}

#include <boost/beast/core.hpp>
#include <boost/beast/http.hpp>
#include <boost/beast/version.hpp>
#include <boost/asio.hpp>
#include <array>
#include <set>
#include <vector>
#include <cstdlib>
#include <iostream>
#include <memory>
#include <string>
#include <cstdint>
#include <thread>
#include <mutex>

namespace asio = boost::asio;
namespace ip = asio::ip;             // from <boost/asio.hpp>
using tcp = asio::ip::tcp;           // from <boost/asio.hpp>
namespace http = boost::beast::http; // from <boost/beast/http.hpp>

boost::string_view appSecret;

std::mutex nnMutex;
network *ann;
char **classNames;

template <class Stream, bool isRequest, class Body, class Fields>
void httpSend(Stream &stream, bool &close, boost::system::error_code &ec, http::message<isRequest, Body, Fields> &&msg)
{
    close = msg.need_eof();
    http::serializer<isRequest, Body, Fields> s{msg};
    http::write(stream, s, ec);
}

std::string findObjects(cv::Mat m)
{
    int nboxes = 0;
    detection *dets_c = nullptr;
    layer l = ann->layers[ann->n - 1];
    {
        std::lock_guard<std::mutex> lg(nnMutex);
        image im;
        im = mat_to_image(m);
        image sized = letterbox_image(im, ann->w, ann->h);
        float *X = sized.data;
        network_predict(ann, X);
        dets_c = get_network_boxes(ann, im.w, im.h, 0.4f, 0.4f, 0, 1, &nboxes);
        free_image(im);
    }
    assert(dets_c != nullptr);

    struct ClassResult
    {
        boost::string_view name;
        float prob;
    };

    struct CObject
    {
        std::array<float, 4> bbox;
        std::vector<ClassResult> classes;
        bool mark;
    };

    std::vector<CObject> cobjs;

    for (int i = 0; i < nboxes; i++)
    {
        CObject tmp{};
        tmp.mark = false;
        tmp.bbox[0] = dets_c[i].bbox.x;
        tmp.bbox[1] = dets_c[i].bbox.y;
        tmp.bbox[2] = dets_c[i].bbox.w;
        tmp.bbox[3] = dets_c[i].bbox.h;
        for (int C = 0; C < l.classes; C++)
        {
            if (dets_c[i].prob[C] > 0.4f)
            {
                tmp.classes.push_back({classNames[C], dets_c[i].prob[C]});
            }
        }
        std::sort(tmp.classes.begin(), tmp.classes.end(), [](const ClassResult &A, const ClassResult &B) {
            return A.prob > B.prob;
        });
        if (tmp.classes.size() > 0)
        {
            cobjs.emplace_back(std::move(tmp));
        }
    }

    std::vector<std::set<size_t>> clusters;

    // Create clusters
    for (int i = 0; i < cobjs.size(); i++)
    {
        if (cobjs[i].mark)
        {
            continue;
        }
        cobjs[i].mark = true;
        clusters.emplace_back();
        clusters.back().insert(i);
        for (int j = i + 1; j < cobjs.size(); j++)
        {
            if (cobjs[j].mark)
            {
                continue;
            }
            if (cobjs[i].classes.front().name != cobjs[j].classes.front().name)
            {
                continue;
            }
            float dx = cobjs[i].bbox[0] - cobjs[j].bbox[0];
            float dy = cobjs[i].bbox[1] - cobjs[j].bbox[1];
            float rx = (cobjs[i].bbox[2] + cobjs[j].bbox[2]) / 2.0f;
            float ry = (cobjs[i].bbox[3] + cobjs[j].bbox[3]) / 2.0f;
            float distance = dx * dx + dy * dy;
            if (distance < 0.25f * rx * ry)
            {
                cobjs[j].mark = true;
                clusters.back().insert(j);
            }
        }
    }

    std::vector<CObject> newcobjs;
    for (auto &cluster : clusters)
    {
        CObject o;
        o.bbox.fill(0.0f);
        for (auto &mergedObjIdx : cluster)
        {
            auto &mergedObj = cobjs[mergedObjIdx];
            o.bbox[0] += mergedObj.bbox[0];
            o.bbox[1] += mergedObj.bbox[1];
            o.bbox[2] += mergedObj.bbox[2];
            o.bbox[3] += mergedObj.bbox[3];
        }
        o.bbox[0] /= cluster.size();
        o.bbox[1] /= cluster.size();
        o.bbox[2] /= cluster.size();
        o.bbox[3] /= cluster.size();
        o.classes = cobjs[*cluster.begin()].classes;
        newcobjs.emplace_back(std::move(o));
    }

    std::cerr << "Clustered " << cobjs.size() << " objects into " << newcobjs.size() << " groups." << std::endl;

    std::stringstream jsonss;
    jsonss << R"({"results": [)"
              "\n";
    bool hasoutercomma = false;
    for (auto &box : newcobjs)
    {
        bool started = false;
        for (auto &cls : box.classes)
        {
            if (!started)
            {
                started = true;
                if (hasoutercomma)
                {
                    jsonss << ",";
                }
                jsonss << R"({"bbox":[)" << box.bbox[0] << "," << box.bbox[1] << "," << box.bbox[2] << "," << box.bbox[3] << "],\n ";
                jsonss << R"("classes": [")";
                jsonss << cls.name << '"';
            }
            else
            {
                jsonss << R"(, ")" << cls.name << '"';
            }
        }
        if (started)
        {
            jsonss << R"(]})"
                   << "\n";
            hasoutercomma = true;
        }
    }
    jsonss << "\n]}\n";
    return jsonss.str();
}

void handleConnection(tcp::socket &sock)
{
    std::cerr << "New connection from " << sock.remote_endpoint().address() << ":" << sock.remote_endpoint().port() << std::endl;

    bool close = false;
    boost::system::error_code ec;

    boost::beast::flat_buffer buffer;

    while (!close)
    {
        http::request<http::string_body> rq;

        auto const badRequest = [&rq](http::status status, boost::beast::string_view reason) {
            http::response<http::string_body> res{status, rq.version()};
            res.set(http::field::content_type, "text/html");
            res.keep_alive(rq.keep_alive());
            res.body() = reason.to_string();
            res.prepare_payload();
            return res;
        };

        http::read(sock, buffer, rq, ec);
        if (ec == http::error::end_of_stream)
        {
            break;
        }
        if (ec)
        {
            std::cerr << "HTTP error: " << ec << std::endl;
            return;
        }
        if (rq.method() != http::verb::post)
        {
            httpSend(sock, close, ec, badRequest(http::status::bad_request, "Unknown HTTP-method\n"));
            std::cerr << "HTTP Error at line " << __LINE__ << std::endl;
            break;
        }
        auto fSecret = rq.find("x-secret");
        if (fSecret == rq.end())
        {
            httpSend(sock, close, ec, badRequest(http::status::unauthorized, "Unauthorized access\n"));
            std::cerr << "HTTP Error at line " << __LINE__ << std::endl;
            break;
        }
        if (fSecret->value().compare(appSecret) != 0)
        {
            httpSend(sock, close, ec, badRequest(http::status::forbidden, "Forbidden access\n"));
            std::cerr << "HTTP Error at line " << __LINE__ << std::endl;
            break;
        }
        auto fType = rq.find("content-type");
        if (fType == rq.end())
        {
            httpSend(sock, close, ec, badRequest(http::status::not_implemented, "Unknown request\n"));
            std::cerr << "HTTP Error at line " << __LINE__ << std::endl;
            break;
        }
        auto vType = fType->value();
        boost::string_view boundary;
        if (!vType.starts_with("multipart/form-data"))
        {
            httpSend(sock, close, ec, badRequest(http::status::not_implemented, "Unknown request\n"));
            std::cerr << "HTTP Error at line " << __LINE__ << std::endl;
            break;
        }
        else
        {
            if (vType.find_first_of('=') == vType.npos)
            {
                httpSend(sock, close, ec, badRequest(http::status::not_acceptable, "Malformed request\n"));
                std::cerr << "HTTP Error at line " << __LINE__ << std::endl;
                break;
            }
            boundary = vType;
            boundary.remove_prefix(vType.find_first_of('=') + 1);
            if (boundary.length() == 0)
            {
                httpSend(sock, close, ec, badRequest(http::status::not_acceptable, "Malformed request\n"));
                std::cerr << "HTTP Error at line " << __LINE__ << std::endl;
                break;
            }
        }
        /*for (auto &a : rq)
        {
            std::cout << "F <" << a.name_string() << "> := <" << a.value() << ">" << std::endl;
        }*/

        auto bodyStorage = rq.body();
        boost::string_view body{bodyStorage};
        auto postfixPos = [](boost::string_view const &haystack, boost::string_view const &needle) -> size_t {
            return static_cast<size_t>(std::search(haystack.begin(), haystack.end(), needle.begin(), needle.end()) - haystack.begin());
        };
        // --BOUND\n
        body.remove_prefix(postfixPos(body, boundary) + boundary.size() + 2);
        // Content-Disposition
        if (!body.starts_with("Content-Disposition"))
        {
            httpSend(sock, close, ec, badRequest(http::status::not_acceptable, "Malformed request\n"));
            std::cerr << "HTTP Error at line " << __LINE__ << std::endl;
            break;
        }
        body.remove_prefix(body.find_first_of('\n', 0) + 1);
        // Content-Type
        if (!body.starts_with("Content-Type"))
        {
            httpSend(sock, close, ec, badRequest(http::status::not_acceptable, "Malformed request\n"));
            std::cerr << "HTTP Error at line " << __LINE__ << std::endl;
            break;
        }
        body.remove_prefix(body.find_first_of('\n', 0) + 1);
        // Content-Length
        if (!body.starts_with("Content-Length"))
        {
            httpSend(sock, close, ec, badRequest(http::status::not_acceptable, "Malformed request\n"));
            std::cerr << "HTTP Error at line " << __LINE__ << std::endl;
            break;
        }
        body.remove_prefix(body.find_first_of('\n', 0) + 1);
        while (body.size() && (body[0] == '\n' || body[0] == '\r'))
        {
            body.remove_prefix(1);
        }
        // terminating boundary
        size_t it = postfixPos(body, boundary);
        while (body.at(it) != '\n')
            it--;
        body = body.substr(0, it);
        // body is now the image

        cv::Mat m;
        {
            std::vector<uint8_t> bodyPixels{body.begin(), body.end()};
            m = cv::imdecode(bodyPixels, 1);
        }
        if (!m.data)
        {
            httpSend(sock, close, ec, badRequest(http::status::not_acceptable, "Malformed request\n"));
            std::cerr << "Malformed image at line " << __LINE__ << std::endl;
            break;
        }
        std::string json = findObjects(m);

        http::response<http::string_body> res{std::piecewise_construct, std::make_tuple(json), std::make_tuple(http::status::ok, rq.version())};
        res.set(http::field::content_type, "application/json");
        res.content_length(json.size());
        res.keep_alive(rq.keep_alive());
        return httpSend(sock, close, ec, std::move(res));
    }

    sock.shutdown(tcp::socket::shutdown_both);
}

int main(int argc, char **argv)
{
    try
    {
        if (argc != 7)
        {
            std::cerr << "Usage: " << argv[0] << " <bind address> <bind port> <secret> <data> <cfg> <weights>" << std::endl;
            return EXIT_FAILURE;
        }

        auto const bindAddr = ip::make_address(argv[1]);
        uint16_t bindPort = static_cast<uint16_t>(std::strtol(argv[2], nullptr, 10));
        appSecret = argv[3];
        //std::cerr << argv[2] << " == " << bindPort << std::endl;

        // Initialize NN
        {
            std::cerr << "Loading NN..." << std::endl;
            cuda_set_device(0);
            float thresh = 0.25f;
            float hier_thresh = 0.4f;
            char *datafile = argv[4];
            char *cfgfile = argv[5];
            char *weights = argv[6];

            list *options = read_data_cfg(datafile);
            int classes = option_find_int(options, const_cast<char *>("classes"), 20);
            char *name_list = option_find_str(options, const_cast<char *>("names"),
                                              const_cast<char *>("darknet/data/names.list"));
            classNames = get_labels(name_list);

            network *net = load_network(cfgfile, weights, 0);
            set_batch_network(net, 1);
            ann = net;
            std::cerr << "Loaded NN" << std::endl;
        }

        asio::io_context ioc{1};

        tcp::acceptor acceptor{ioc, {bindAddr, bindPort}};

        while (true)
        {
            tcp::socket sock{ioc};
            acceptor.accept(sock);
            std::thread{std::bind(&handleConnection, std::move(sock))}.detach();
        }
    }
    catch (std::exception const &e)
    {
        std::cerr << "Exception:" << e.what() << std::endl;
        return EXIT_FAILURE;
    }
    return EXIT_SUCCESS;
}
