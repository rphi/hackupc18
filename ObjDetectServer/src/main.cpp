
#include "objdetserver.h"

#include <boost/beast/core.hpp>
#include <boost/beast/http.hpp>
#include <boost/beast/version.hpp>
#include <boost/asio.hpp>
#include <cstdlib>
#include <iostream>
#include <memory>
#include <string>
#include <cstdint>
#include <thread>

namespace asio = boost::asio;
namespace ip = asio::ip;             // from <boost/asio.hpp>
using tcp = asio::ip::tcp;           // from <boost/asio.hpp>
namespace http = boost::beast::http; // from <boost/beast/http.hpp>

boost::string_view appSecret;

template <class Stream, bool isRequest, class Body, class Fields>
void httpSend(Stream &stream, bool &close, boost::system::error_code &ec, http::message<isRequest, Body, Fields> &&msg)
{
    close = msg.need_eof();
    http::serializer<isRequest, Body, Fields> s{msg};
    http::write(stream, s, ec);
}

void handleConnection(tcp::socket &sock)
{
    std::cout << "New connection from " << sock.remote_endpoint().address() << ":" << sock.remote_endpoint().port() << std::endl;

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
            break;
        }
        auto fType = rq.find("content-type");
        if (fType == rq.end()) {
            httpSend(sock, close, ec, badRequest(http::status::not_implemented, "Unknown request\n"));
            break;
        }
        if (!fType->value().starts_with("multipart/form-data")) {
            httpSend(sock, close, ec, badRequest(http::status::not_implemented, "Unknown request\n"));
            break;
        }
        for(auto& a : rq) {
            std::cout << "F <" << a.name_string() << "> := <" << a.value() << ">" << std::endl;
        }
        auto fSecret = rq.find("x-secret");
        if (fSecret == rq.end())
        {
            httpSend(sock, close, ec, badRequest(http::status::unauthorized, "Unauthorized access\n"));
            break;
        }
        if (fSecret->value().compare(appSecret) != 0)
        {
            httpSend(sock, close, ec, badRequest(http::status::forbidden, "Forbidden access\n"));
            break;
        }
        std::string json = "{}\n";
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
        if (argc != 6)
        {
            std::cerr << "Usage: " << argv[0] << " <bind address> <bind port> <secret> <cfg> <weights>" << std::endl;
            return EXIT_FAILURE;
        }

        auto const bindAddr = ip::make_address(argv[1]);
        uint16_t bindPort = static_cast<uint16_t>(std::strtol(argv[2], nullptr, 10));
        appSecret = argv[3];
        //std::cerr << argv[2] << " == " << bindPort << std::endl;

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