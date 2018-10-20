
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

void handleConnection(tcp::socket &sock)
{
    std::cout << "New connection from " << sock.remote_endpoint().address() << ":" << sock.remote_endpoint().port() << std::endl;
    sock.shutdown(tcp::socket::shutdown_both);
}

int main(int argc, char **argv)
{
    try
    {
        if (argc != 5)
        {
            std::cerr << "Usage: " << argv[0] << " <bind address> <bind port> <cfg> <weights>" << std::endl;
            return EXIT_FAILURE;
        }

        auto const bindAddr = ip::make_address(argv[1]);
        uint16_t bindPort = static_cast<uint16_t>(std::strtol(argv[2], nullptr, 10));
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
